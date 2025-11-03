package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import com.gravityyfh.roleplaycity.town.manager.TownPoliceManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TownPoliceGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownPoliceManager policeManager;

    private static final String POLICE_TITLE = ChatColor.DARK_BLUE + "🚔 Police Municipale";
    private static final String ISSUE_FINE_TITLE = ChatColor.DARK_BLUE + "📝 Émettre Amende";

    private final Map<UUID, FineContext> pendingFines;

    public TownPoliceGUI(RoleplayCity plugin, TownManager townManager, TownPoliceManager policeManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.policeManager = policeManager;
        this.pendingFines = new HashMap<>();
    }

    public void openPoliceMenu(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous devez être dans une ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());
        if (role != TownRole.POLICIER && role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            player.sendMessage(ChatColor.RED + "Vous devez être policier pour accéder à ce menu.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, POLICE_TITLE);

        // Statistiques
        var stats = policeManager.getTownStatistics(townName);

        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.GOLD + "Statistiques");
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ChatColor.GRAY + "Total amendes: " + ChatColor.WHITE + stats.totalFines);
        statsLore.add(ChatColor.GRAY + "Payées: " + ChatColor.GREEN + stats.paidFines);
        statsLore.add(ChatColor.GRAY + "Contestées: " + ChatColor.YELLOW + stats.contestedFines);
        statsLore.add(ChatColor.GRAY + "En attente: " + ChatColor.RED + stats.pendingFines);
        statsLore.add("");
        statsLore.add(ChatColor.GRAY + "Montant total: " + ChatColor.GOLD + String.format("%.2f€", stats.totalAmount));
        statsLore.add(ChatColor.GRAY + "Collecté: " + ChatColor.GREEN + String.format("%.2f€", stats.collectedAmount));
        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        inv.setItem(4, statsItem);

        // Émettre une amende
        ItemStack issueFineItem = new ItemStack(Material.PAPER);
        ItemMeta issueFineMeta = issueFineItem.getItemMeta();
        issueFineMeta.setDisplayName(ChatColor.RED + "Émettre une Amende");
        List<String> issueFineLore = new ArrayList<>();
        issueFineLore.add(ChatColor.GRAY + "Infliger une amende");
        issueFineLore.add(ChatColor.GRAY + "à un citoyen");
        issueFineLore.add("");
        issueFineLore.add(ChatColor.YELLOW + "Cliquez pour commencer");
        issueFineMeta.setLore(issueFineLore);
        issueFineItem.setItemMeta(issueFineMeta);
        inv.setItem(11, issueFineItem);

        // Voir les amendes actives
        ItemStack activeFinesItem = new ItemStack(Material.REDSTONE);
        ItemMeta activeFinesMeta = activeFinesItem.getItemMeta();
        activeFinesMeta.setDisplayName(ChatColor.YELLOW + "Amendes en Attente");
        List<String> activeFinesLore = new ArrayList<>();
        activeFinesLore.add(ChatColor.GRAY + "Voir toutes les amendes");
        activeFinesLore.add(ChatColor.GRAY + "qui n'ont pas été payées");
        activeFinesLore.add("");
        activeFinesLore.add(ChatColor.WHITE + "Total: " + stats.pendingFines);
        activeFinesLore.add(ChatColor.YELLOW + "Cliquez pour voir");
        activeFinesMeta.setLore(activeFinesLore);
        activeFinesItem.setItemMeta(activeFinesMeta);
        inv.setItem(13, activeFinesItem);

        // Amendes contestées
        ItemStack contestedItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta contestedMeta = contestedItem.getItemMeta();
        contestedMeta.setDisplayName(ChatColor.GOLD + "Amendes Contestées");
        List<String> contestedLore = new ArrayList<>();
        contestedLore.add(ChatColor.GRAY + "Voir les amendes");
        contestedLore.add(ChatColor.GRAY + "en attente de jugement");
        contestedLore.add("");
        contestedLore.add(ChatColor.WHITE + "Total: " + stats.contestedFines);
        contestedLore.add(ChatColor.YELLOW + "Cliquez pour voir");
        contestedMeta.setLore(contestedLore);
        contestedItem.setItemMeta(contestedMeta);
        inv.setItem(15, contestedItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!title.equals(POLICE_TITLE)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Émettre une Amende")) {
            handleIssueFine(player);
        } else if (displayName.contains("Fermer")) {
            player.closeInventory();
        }
        // TODO: Implémenter l'affichage des listes d'amendes
    }

    private void handleIssueFine(Player player) {
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "=== ÉMETTRE UNE AMENDE ===");
        player.sendMessage(ChatColor.YELLOW + "Étape 1/3: Entrez le nom du joueur à verbaliser");
        player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour abandonner)");

        pendingFines.put(player.getUniqueId(), new FineContext());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        FineContext context = pendingFines.get(player.getUniqueId());

        if (context == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            pendingFines.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Émission d'amende annulée.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            processStep(player, context, input);
        });
    }

    private void processStep(Player player, FineContext context, String input) {
        switch (context.step) {
            case 0 -> { // Nom du joueur
                Player target = Bukkit.getPlayer(input);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Joueur introuvable. Réessayez ou tapez 'annuler'.");
                    return;
                }

                String townName = townManager.getPlayerTown(player.getUniqueId());
                Town town = townManager.getTown(townName);

                // Vérifier que le joueur est dans la même ville
                if (!town.isMember(target.getUniqueId())) {
                    player.sendMessage(ChatColor.RED + "Ce joueur n'est pas membre de votre ville.");
                    return;
                }

                context.targetUuid = target.getUniqueId();
                context.targetName = target.getName();
                context.step = 1;

                player.sendMessage(ChatColor.GREEN + "Joueur: " + target.getName());
                player.sendMessage(ChatColor.YELLOW + "Étape 2/3: Entrez le motif de l'amende");
            }
            case 1 -> { // Motif
                if (input.length() < 5) {
                    player.sendMessage(ChatColor.RED + "Le motif doit contenir au moins 5 caractères.");
                    return;
                }

                context.reason = input;
                context.step = 2;

                player.sendMessage(ChatColor.GREEN + "Motif: " + input);
                player.sendMessage(ChatColor.YELLOW + "Étape 3/3: Entrez le montant de l'amende (en €)");
            }
            case 2 -> { // Montant
                try {
                    double amount = Double.parseDouble(input);
                    if (amount <= 0) {
                        player.sendMessage(ChatColor.RED + "Le montant doit être positif.");
                        return;
                    }

                    if (amount > 10000) {
                        player.sendMessage(ChatColor.RED + "Le montant maximum est de 10000€.");
                        return;
                    }

                    // Émettre l'amende
                    String townName = townManager.getPlayerTown(player.getUniqueId());
                    Fine fine = policeManager.issueFine(
                        townName,
                        context.targetUuid,
                        context.targetName,
                        player,
                        context.reason,
                        amount
                    );

                    if (fine != null) {
                        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage(ChatColor.GREEN + "   Amende émise avec succès !");
                        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                        player.sendMessage(ChatColor.GRAY + "Joueur: " + ChatColor.YELLOW + context.targetName);
                        player.sendMessage(ChatColor.GRAY + "Motif: " + ChatColor.WHITE + context.reason);
                        player.sendMessage(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + amount + "€");
                        player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    } else {
                        player.sendMessage(ChatColor.RED + "Erreur lors de l'émission de l'amende.");
                    }

                    pendingFines.remove(player.getUniqueId());

                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Veuillez entrer un nombre valide.");
                }
            }
        }
    }

    private static class FineContext {
        int step = 0;
        UUID targetUuid;
        String targetName;
        String reason;
    }
}
