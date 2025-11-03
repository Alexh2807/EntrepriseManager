package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownJusticeManager;
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

public class TownJusticeGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownPoliceManager policeManager;
    private final TownJusticeManager justiceManager;

    private static final String JUSTICE_TITLE = ChatColor.DARK_PURPLE + "⚖️ Justice Municipale";
    private static final String CASES_TITLE = ChatColor.DARK_PURPLE + "📋 Affaires en Cours";

    private final Map<UUID, JudgementContext> pendingJudgements;

    public TownJusticeGUI(RoleplayCity plugin, TownManager townManager,
                         TownPoliceManager policeManager, TownJusticeManager justiceManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.policeManager = policeManager;
        this.justiceManager = justiceManager;
        this.pendingJudgements = new HashMap<>();
    }

    public void openJusticeMenu(Player player) {
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
        if (role != TownRole.JUGE && role != TownRole.MAIRE) {
            player.sendMessage(ChatColor.RED + "Vous devez être juge pour accéder à ce menu.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, JUSTICE_TITLE);

        // Statistiques
        var stats = justiceManager.getTownStatistics(townName);

        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.GOLD + "Statistiques");
        List<String> statsLore = new ArrayList<>();
        statsLore.add(ChatColor.GRAY + "Total jugements: " + ChatColor.WHITE + stats.totalJudgements);
        statsLore.add(ChatColor.GRAY + "Confirmés: " + ChatColor.RED + stats.confirmedJudgements);
        statsLore.add(ChatColor.GRAY + "Annulés: " + ChatColor.GREEN + stats.cancelledJudgements);
        statsLore.add(ChatColor.GRAY + "En attente: " + ChatColor.YELLOW + stats.pendingContestations);
        statsLore.add("");
        statsLore.add(ChatColor.GRAY + "Taux confirmation: " + ChatColor.AQUA +
            String.format("%.1f%%", stats.getConfirmationRate()));
        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        inv.setItem(4, statsItem);

        // Affaires en cours
        ItemStack casesItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta casesMeta = casesItem.getItemMeta();
        casesMeta.setDisplayName(ChatColor.YELLOW + "Affaires en Cours");
        List<String> casesLore = new ArrayList<>();
        casesLore.add(ChatColor.GRAY + "Voir les contestations");
        casesLore.add(ChatColor.GRAY + "en attente de jugement");
        casesLore.add("");
        casesLore.add(ChatColor.WHITE + "Total: " + stats.pendingContestations);
        casesLore.add(ChatColor.YELLOW + "Cliquez pour voir");
        casesMeta.setLore(casesLore);
        casesItem.setItemMeta(casesMeta);
        inv.setItem(13, casesItem);

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    public void openCasesMenu(Player player) {
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            return;
        }

        List<Fine> contestedFines = policeManager.getContestedFines(townName);

        Inventory inv = Bukkit.createInventory(null, 54, CASES_TITLE);

        int slot = 0;
        for (Fine fine : contestedFines) {
            if (slot >= 45) break;

            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + fine.getOffenderName());

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Motif: " + ChatColor.WHITE + fine.getReason());
            lore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + fine.getAmount() + "€");
            lore.add(ChatColor.GRAY + "Policier: " + ChatColor.YELLOW + fine.getPolicierName());
            lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + fine.getIssueDate().toLocalDate());
            lore.add("");
            lore.add(ChatColor.YELLOW + "Cliquez pour juger");

            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }

        // Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        if (title.equals(JUSTICE_TITLE)) {
            handleJusticeMenuClick(event);
        } else if (title.equals(CASES_TITLE)) {
            handleCasesMenuClick(event);
        }
    }

    private void handleJusticeMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Affaires en Cours")) {
            player.closeInventory();
            openCasesMenu(player);
        } else if (displayName.contains("Fermer")) {
            player.closeInventory();
        }
    }

    private void handleCasesMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Retour")) {
            player.closeInventory();
            openJusticeMenu(player);
        } else if (clicked.getType() == Material.PAPER) {
            // Récupérer l'amende correspondante
            String offenderName = displayName;
            String townName = townManager.getPlayerTown(player.getUniqueId());
            List<Fine> contestedFines = policeManager.getContestedFines(townName);

            Fine selectedFine = null;
            for (Fine fine : contestedFines) {
                if (fine.getOffenderName().equals(offenderName)) {
                    selectedFine = fine;
                    break;
                }
            }

            if (selectedFine != null) {
                handleJudgeFine(player, selectedFine);
            }
        }
    }

    private void handleJudgeFine(Player player, Fine fine) {
        player.closeInventory();

        // Afficher le dossier
        player.sendMessage(justiceManager.getFineReview(fine));
        player.sendMessage(ChatColor.GOLD + "=== RENDRE VOTRE JUGEMENT ===");
        player.sendMessage(ChatColor.YELLOW + "Tapez 'valide' pour confirmer l'amende");
        player.sendMessage(ChatColor.YELLOW + "Tapez 'invalide' pour annuler l'amende");
        player.sendMessage(ChatColor.GRAY + "(Tapez 'annuler' pour abandonner)");

        pendingJudgements.put(player.getUniqueId(), new JudgementContext(fine));
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        JudgementContext context = pendingJudgements.get(player.getUniqueId());

        if (context == null) {
            return;
        }

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler")) {
            pendingJudgements.remove(player.getUniqueId());
            player.sendMessage(ChatColor.YELLOW + "Jugement annulé.");
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            processJudgement(player, context, input);
        });
    }

    private void processJudgement(Player player, JudgementContext context, String input) {
        if (context.step == 0) {
            // Décision
            if (input.equalsIgnoreCase("valide")) {
                context.valid = true;
                context.step = 1;
                player.sendMessage(ChatColor.GREEN + "Décision: Amende CONFIRMÉE");
                player.sendMessage(ChatColor.YELLOW + "Maintenant, entrez votre verdict (explication):");
            } else if (input.equalsIgnoreCase("invalide")) {
                context.valid = false;
                context.step = 1;
                player.sendMessage(ChatColor.RED + "Décision: Amende ANNULÉE");
                player.sendMessage(ChatColor.YELLOW + "Maintenant, entrez votre verdict (explication):");
            } else {
                player.sendMessage(ChatColor.RED + "Réponse invalide. Tapez 'valide' ou 'invalide'.");
            }
        } else if (context.step == 1) {
            // Verdict
            if (input.length() < 10) {
                player.sendMessage(ChatColor.RED + "Le verdict doit contenir au moins 10 caractères.");
                return;
            }

            // Enregistrer le jugement
            boolean success = justiceManager.judgeFine(context.fine, player, context.valid, input);

            if (success) {
                player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage(ChatColor.GREEN + "   Jugement enregistré !");
                player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage(ChatColor.GRAY + "Décision: " +
                    (context.valid ? ChatColor.RED + "Amende confirmée" : ChatColor.GREEN + "Amende annulée"));
                player.sendMessage(ChatColor.GRAY + "Verdict: " + ChatColor.WHITE + input);
                player.sendMessage(ChatColor.GREEN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }

            pendingJudgements.remove(player.getUniqueId());
        }
    }

    private static class JudgementContext {
        final Fine fine;
        int step = 0;
        boolean valid;

        JudgementContext(Fine fine) {
            this.fine = fine;
        }
    }
}
