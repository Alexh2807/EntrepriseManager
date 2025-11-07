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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class TownPoliceGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownPoliceManager policeManager;

    private static final String POLICE_TITLE = ChatColor.DARK_BLUE + "🚔 Police Municipale";
    private static final String SELECT_PLAYER_TITLE = ChatColor.DARK_BLUE + "👤 Sélectionner un joueur";
    private static final String SELECT_FINE_TITLE = ChatColor.DARK_BLUE + "📋 Type d'amende";

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

    // PHASE 1: Sélection du joueur en ligne
    private void openPlayerSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, SELECT_PLAYER_TITLE);

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int slot = 0;

        for (Player target : onlinePlayers) {
            if (slot >= 45) break;
            if (target.equals(player)) continue; // Ne pas afficher le policier lui-même

            ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
            skullMeta.setOwningPlayer(target);
            skullMeta.setDisplayName(ChatColor.YELLOW + target.getName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + target.getUniqueId().toString().substring(0, 8));
            lore.add("");
            lore.add(ChatColor.GREEN + "Cliquez pour sélectionner");
            skullMeta.setLore(lore);

            playerHead.setItemMeta(skullMeta);
            inv.setItem(slot++, playerHead);
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(53, closeItem);

        player.openInventory(inv);
    }

    // PHASE 2: Sélection de l'amende prédéfinie
    private void openFineSelectionMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, SELECT_FINE_TITLE);

        // Charger les amendes prédéfinies depuis la config
        List<Map<?, ?>> predefinedFines = plugin.getConfig().getMapList("town.predefined-fines");
        int slot = 0;

        for (Map<?, ?> fineData : predefinedFines) {
            if (slot >= 45) break;

            String title = (String) fineData.get("title");
            double amount = ((Number) fineData.get("amount")).doubleValue();
            String description = (String) fineData.get("description");

            ItemStack fineItem = new ItemStack(Material.PAPER);
            ItemMeta fineMeta = fineItem.getItemMeta();
            fineMeta.setDisplayName(ChatColor.RED + title);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + amount + "€");
            lore.add("");
            lore.add(ChatColor.WHITE + description);
            lore.add("");
            lore.add(ChatColor.GREEN + "Cliquez pour sélectionner");
            fineMeta.setLore(lore);

            fineItem.setItemMeta(fineMeta);
            inv.setItem(slot++, fineItem);
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(53, closeItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // Menu principal de la police
        if (title.equals(POLICE_TITLE)) {
            event.setCancelled(true);
            String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

            if (displayName.contains("Émettre une Amende")) {
                // Initialiser le contexte et ouvrir la phase 1
                pendingFines.put(player.getUniqueId(), new FineContext());
                openPlayerSelectionMenu(player);
            } else if (displayName.contains("Fermer")) {
                player.closeInventory();
            }
        }
        // PHASE 1: Sélection du joueur
        else if (title.equals(SELECT_PLAYER_TITLE)) {
            event.setCancelled(true);

            if (clicked.getType() == Material.PLAYER_HEAD) {
                SkullMeta skullMeta = (SkullMeta) clicked.getItemMeta();
                String targetName = ChatColor.stripColor(skullMeta.getDisplayName());
                Player target = Bukkit.getPlayer(targetName);

                if (target != null) {
                    FineContext context = pendingFines.get(player.getUniqueId());
                    if (context != null) {
                        context.targetUuid = target.getUniqueId();
                        context.targetName = target.getName();
                        context.step = 1;

                        // Passer à la phase 2
                        openFineSelectionMenu(player);
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Le joueur n'est plus en ligne.");
                    player.closeInventory();
                    pendingFines.remove(player.getUniqueId());
                }
            } else if (clicked.getType() == Material.ARROW) {
                openPoliceMenu(player);
                pendingFines.remove(player.getUniqueId());
            } else if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
                pendingFines.remove(player.getUniqueId());
            }
        }
        // PHASE 2: Sélection de l'amende
        else if (title.equals(SELECT_FINE_TITLE)) {
            event.setCancelled(true);

            if (clicked.getType() == Material.PAPER) {
                FineContext context = pendingFines.get(player.getUniqueId());
                if (context != null) {
                    String fineTitle = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

                    // Récupérer les informations de l'amende depuis le lore
                    List<String> lore = clicked.getItemMeta().getLore();
                    String amountLine = ChatColor.stripColor(lore.get(0)); // "Montant: 500.0€"
                    double amount = Double.parseDouble(amountLine.replaceAll("[^0-9.]", ""));

                    context.fineTitle = fineTitle;
                    context.amount = amount;
                    context.step = 2;

                    // Passer à la phase 3: demander la description
                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "=== ÉMETTRE UNE AMENDE ===");
                    player.sendMessage(ChatColor.GRAY + "Joueur: " + ChatColor.YELLOW + context.targetName);
                    player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.RED + fineTitle);
                    player.sendMessage(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + amount + "€");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.YELLOW + "Étape 3/3: Entrez une description détaillée");
                    player.sendMessage(ChatColor.GRAY + "(Minimum 20 caractères)");
                    player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour abandonner)");
                }
            } else if (clicked.getType() == Material.ARROW) {
                openPlayerSelectionMenu(player);
            } else if (clicked.getType() == Material.BARRIER) {
                player.closeInventory();
                pendingFines.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        FineContext context = pendingFines.get(player.getUniqueId());

        if (context == null || context.step != 2) {
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
            // Vérifier la longueur minimale
            if (input.length() < 20) {
                player.sendMessage(ChatColor.RED + "La description doit contenir au moins 20 caractères.");
                player.sendMessage(ChatColor.GRAY + "Caractères actuels: " + input.length() + " / 20");
                return;
            }

            // Émettre l'amende
            String townName = townManager.getPlayerTown(player.getUniqueId());
            String fullReason = context.fineTitle + " - " + input;

            Fine fine = policeManager.issueFine(
                townName,
                context.targetUuid,
                context.targetName,
                player,
                fullReason,
                context.amount
            );

            if (fine != null) {
                player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage(ChatColor.GREEN + "   Amende émise avec succès !");
                player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage(ChatColor.GRAY + "Joueur: " + ChatColor.YELLOW + context.targetName);
                player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.RED + context.fineTitle);
                player.sendMessage(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + context.amount + "€");
                player.sendMessage(ChatColor.GRAY + "Description: " + ChatColor.WHITE + input);
                player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            } else {
                player.sendMessage(ChatColor.RED + "Erreur lors de l'émission de l'amende.");
            }

            pendingFines.remove(player.getUniqueId());
        });
    }

    private static class FineContext {
        int step = 0;
        UUID targetUuid;
        String targetName;
        String fineTitle;
        double amount;
    }
}
