package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.MunicipalSubType;
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
    private static final String VERDICT_TITLE = ChatColor.DARK_PURPLE + "⚖ Rendre le Verdict";

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

    public void openVerdictMenu(Player player, Fine fine) {
        Inventory inv = Bukkit.createInventory(null, 27, VERDICT_TITLE);

        // Informations de l'amende
        ItemStack infoItem = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Dossier #" + fine.getFineId().toString().substring(0, 8));
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Contrevenant: " + ChatColor.WHITE + fine.getOffenderName());
        infoLore.add(ChatColor.GRAY + "Policier: " + ChatColor.YELLOW + fine.getPolicierName());
        infoLore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + fine.getAmount() + "€");
        infoLore.add("");
        infoLore.add(ChatColor.DARK_RED + "Motif de l'amende:");
        infoLore.add(ChatColor.WHITE + fine.getReason());
        infoLore.add("");
        if (fine.getContestReason() != null && !fine.getContestReason().isEmpty()) {
            infoLore.add(ChatColor.YELLOW + "Motif de contestation:");
            infoLore.add(ChatColor.WHITE + fine.getContestReason());
        }
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Bouton CONFIRMER (vert)
        ItemStack confirmItem = new ItemStack(Material.LIME_WOOL);
        ItemMeta confirmMeta = confirmItem.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "✔ CONFIRMER L'AMENDE");
        List<String> confirmLore = new ArrayList<>();
        confirmLore.add("");
        confirmLore.add(ChatColor.GRAY + "L'amende sera maintenue");
        confirmLore.add(ChatColor.GRAY + "Le contrevenant devra payer");
        confirmLore.add(ChatColor.GRAY + "Le policier recevra sa commission");
        confirmLore.add("");
        confirmLore.add(ChatColor.GREEN + "Cliquez pour confirmer");
        confirmMeta.setLore(confirmLore);
        confirmItem.setItemMeta(confirmMeta);
        inv.setItem(11, confirmItem);

        // Bouton ANNULER (rouge)
        ItemStack cancelItem = new ItemStack(Material.RED_WOOL);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "✖ ANNULER L'AMENDE");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add("");
        cancelLore.add(ChatColor.GRAY + "L'amende sera annulée");
        cancelLore.add(ChatColor.GRAY + "Le contrevenant ne paiera rien");
        cancelLore.add(ChatColor.GRAY + "Le policier paiera votre commission");
        cancelLore.add("");
        cancelLore.add(ChatColor.RED + "Cliquez pour annuler");
        cancelMeta.setLore(cancelLore);
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(15, cancelItem);

        // Bouton Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

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
            lore.add(ChatColor.GRAY + "Motif amende: " + ChatColor.WHITE + fine.getReason());
            lore.add(ChatColor.GRAY + "Montant: " + ChatColor.GOLD + fine.getAmount() + "€");
            lore.add(ChatColor.GRAY + "Policier: " + ChatColor.YELLOW + fine.getPolicierName());
            lore.add(ChatColor.GRAY + "Date: " + ChatColor.WHITE + fine.getIssueDate().toLocalDate());
            lore.add("");
            if (fine.getContestReason() != null && !fine.getContestReason().isEmpty()) {
                lore.add(ChatColor.YELLOW + "Motif contestation:");
                lore.add(ChatColor.WHITE + fine.getContestReason());
                lore.add("");
            }
            lore.add(ChatColor.GREEN + "Cliquez pour juger");

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
        } else if (title.equals(VERDICT_TITLE)) {
            handleVerdictMenuClick(event);
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

        // NPE Guard: Vérifier que l'item a une metadata et un displayName
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
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

        // NPE Guard: Vérifier que l'item a une metadata et un displayName
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
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
                player.closeInventory();
                openVerdictMenu(player, selectedFine);
            }
        }
    }

    private void handleVerdictMenuClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // NPE Guard: Vérifier que l'item a une metadata et un displayName
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());

        if (displayName.contains("Retour")) {
            player.closeInventory();
            openCasesMenu(player);
            return;
        }

        // Récupérer l'ID de l'amende depuis le dossier
        ItemStack dossierItem = event.getInventory().getItem(4);
        if (dossierItem == null || dossierItem.getType() != Material.PAPER) {
            return;
        }

        // NPE Guard: Vérifier que le dossier a une metadata et un displayName
        if (!dossierItem.hasItemMeta() || dossierItem.getItemMeta().getDisplayName() == null) {
            return;
        }

        String dossierName = ChatColor.stripColor(dossierItem.getItemMeta().getDisplayName());
        String fineIdPrefix = dossierName.replace("Dossier #", "");

        // Trouver l'amende correspondante
        String townName = townManager.getPlayerTown(player.getUniqueId());
        List<Fine> contestedFines = policeManager.getContestedFines(townName);
        Fine selectedFine = null;

        for (Fine fine : contestedFines) {
            if (fine.getFineId().toString().startsWith(fineIdPrefix)) {
                selectedFine = fine;
                break;
            }
        }

        if (selectedFine == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Amende introuvable");
            player.closeInventory();
            return;
        }

        // Gestion du choix
        if (displayName.contains("CONFIRMER")) {
            handleJudgeFine(player, selectedFine, true);
        } else if (displayName.contains("ANNULER")) {
            handleJudgeFine(player, selectedFine, false);
        }
    }

    private void handleJudgeFine(Player player, Fine fine, boolean valid) {
        player.closeInventory();

        // Vérifier que le juge est sur un terrain TRIBUNAL
        Plot currentPlot = plugin.getClaimManager().getPlotAt(player.getLocation().getChunk());

        if (currentPlot == null ||
            currentPlot.getMunicipalSubType() != MunicipalSubType.TRIBUNAL) {
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c✖ §lJUGEMENT IMPOSSIBLE");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§7Vous devez être dans un TRIBUNAL");
            player.sendMessage("§7pour rendre un jugement");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§e⚖ Rendez-vous au tribunal municipal");
            return;
        }

        // Afficher la décision
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        if (valid) {
            player.sendMessage("§a§lDÉCISION: AMENDE CONFIRMÉE");
        } else {
            player.sendMessage("§c§lDÉCISION: AMENDE ANNULÉE");
        }
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§7Maintenant, entrez votre verdict (explication):");
        player.sendMessage("§7Minimum 10 caractères");
        player.sendMessage("§7(Tapez 'annuler' pour abandonner)");

        JudgementContext context = new JudgementContext(fine);
        context.valid = valid;
        pendingJudgements.put(player.getUniqueId(), context);
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
        // Verdict texte
        if (input.length() < 10) {
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c✖ Verdict trop court");
            player.sendMessage("§7Le verdict doit contenir au moins 10 caractères");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return;
        }

        // Enregistrer le jugement
        boolean success = justiceManager.judgeFine(context.fine, player, context.valid, input);

        if (success) {
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§a§l✔ JUGEMENT ENREGISTRÉ");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§7Décision: " +
                (context.valid ? "§aAmende confirmée" : "§cAmende annulée"));
            player.sendMessage("§7Verdict: §f" + input);
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }

        pendingJudgements.remove(player.getUniqueId());
    }

    private static class JudgementContext {
        final Fine fine;
        boolean valid;

        JudgementContext(Fine fine) {
            this.fine = fine;
        }
    }
}
