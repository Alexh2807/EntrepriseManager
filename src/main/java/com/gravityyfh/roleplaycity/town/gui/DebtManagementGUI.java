package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.CompanyPlotManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * GUI pour gérer les dettes d'un joueur (particulier + entreprise)
 */
public class DebtManagementGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownMainGUI mainGUI;
    private final com.gravityyfh.roleplaycity.town.manager.DebtNotificationService debtNotificationService;

    public DebtManagementGUI(RoleplayCity plugin, TownManager townManager, TownMainGUI mainGUI, com.gravityyfh.roleplaycity.town.manager.DebtNotificationService debtNotificationService) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.mainGUI = mainGUI;
        this.debtNotificationService = debtNotificationService;
    }

    /**
     * Ouvre le menu de gestion des dettes pour le joueur
     */
    public void openDebtMenu(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return;
        }

        // Récupérer toutes les dettes du joueur
        List<Town.PlayerDebt> debts = town.getPlayerDebts(player.getUniqueId());

        if (debts.isEmpty()) {
            player.sendMessage(ChatColor.GREEN + "✓ Vous n'avez aucune dette !");
            return;
        }

        // Créer l'inventaire
        int size = Math.min(54, ((debts.size() + 8) / 9) * 9); // Taille dynamique
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.RED + "🔴 Gestion des Dettes");

        // Remplir l'inventaire avec les dettes
        for (int i = 0; i < debts.size() && i < 45; i++) {
            Town.PlayerDebt debt = debts.get(i);
            inv.setItem(i, createDebtItem(debt, town));
        }

        // Bouton Retour au menu principal
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
            List<String> backLore = new ArrayList<>();
            backLore.add(ChatColor.GRAY + "Retour au menu ville");
            backMeta.setLore(backLore);
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(size - 9, backButton);

        // Bouton de fermeture
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(ChatColor.RED + "✖ Fermer");
            closeButton.setItemMeta(closeMeta);
        }
        inv.setItem(size - 1, closeButton);

        player.openInventory(inv);
    }

    /**
     * Crée un item représentant une dette
     */
    private ItemStack createDebtItem(Town.PlayerDebt debt, Town town) {
        Plot plot = debt.plot();
        boolean isCompanyDebt = (plot.getCompanyDebtAmount() > 0);
        boolean isGroup = plot.isGrouped();

        // Matériau selon le type de dette
        Material material;
        if (isCompanyDebt) {
            material = Material.RED_CONCRETE; // Entreprise
        } else {
            material = Material.REDSTONE_BLOCK; // Particulier
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Titre
            if (isGroup) {
                meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD +
                    (isCompanyDebt ? "💼 Dette Entreprise - Groupe" : "🏠 Dette Particulier - Groupe"));
            } else {
                meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD +
                    (isCompanyDebt ? "💼 Dette Entreprise" : "🏠 Dette Particulier"));
            }

            // Lore (description)
            List<String> lore = new ArrayList<>();
            lore.add("");

            lore.add(ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE + (isGroup ? "Terrain groupé" : plot.getCoordinates()));

            lore.add(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + town.getName());
            lore.add("");
            lore.add(ChatColor.RED + "Dette: " + ChatColor.GOLD + String.format("%.2f€", debt.amount()));

            // Date d'avertissement et temps restant précis
            if (debt.warningDate() != null) {
                lore.add(ChatColor.GRAY + "Depuis: " + debt.warningDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

                // Afficher le temps restant précis (jours, heures, minutes)
                Plot.DebtTimeRemaining timeRemaining = debt.getTimeRemaining();
                if (timeRemaining != null) {
                    if (!timeRemaining.isExpired()) {
                        lore.add(ChatColor.YELLOW + "⚠ Saisie dans: " + ChatColor.RED + timeRemaining.formatDetailed());
                    } else {
                        lore.add(ChatColor.DARK_RED + "⚠ SAISIE IMMINENTE !");
                    }
                }
            }

            lore.add("");

            // Instructions de paiement
            if (isCompanyDebt) {
                String companyName = plot.getCompanyName() != null ? plot.getCompanyName() : "Entreprise";
                lore.add(ChatColor.GREEN + "➜ CLIC GAUCHE: PAYER");
                lore.add("");
                lore.add(ChatColor.GRAY + "💡 Pour recharger l'entreprise:");
                lore.add(ChatColor.GRAY + "  1. Tapez /entreprise");
                lore.add(ChatColor.GRAY + "  2. Cliquez sur 'Mes Entreprises'");
                lore.add(ChatColor.GRAY + "  3. Sélectionnez '" + companyName + "'");
                lore.add(ChatColor.GRAY + "  4. Cliquez sur 'Déposer Argent'");
            } else {
                lore.add(ChatColor.GREEN + "➜ CLIC GAUCHE: PAYER");
                lore.add(ChatColor.GRAY + "   (argent personnel)");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();
        if (!title.equals(ChatColor.RED + "🔴 Gestion des Dettes")) {
            return;
        }

        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Bouton Retour
        if (clickedItem.getType() == Material.ARROW) {
            player.closeInventory();
            // Retour au menu principal de la ville
            if (mainGUI != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    mainGUI.openMainMenu(player);
                }, 1L);
            }
            return;
        }

        // Bouton Fermer
        if (clickedItem.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        // Clic sur une dette
        if (clickedItem.getType() == Material.RED_CONCRETE || clickedItem.getType() == Material.REDSTONE_BLOCK) {
            // Trouver la ville actuelle
            String townName = findPlayerTownWithDebts(player);
            if (townName == null) {
                player.sendMessage(ChatColor.RED + "Impossible de trouver la ville.");
                player.closeInventory();
                return;
            }

            Town town = townManager.getTown(townName);
            if (town == null) {
                player.sendMessage(ChatColor.RED + "Ville introuvable.");
                player.closeInventory();
                return;
            }

            // Récupérer les dettes
            List<Town.PlayerDebt> debts = town.getPlayerDebts(player.getUniqueId());
            int slotIndex = event.getSlot();

            if (slotIndex < 0 || slotIndex >= debts.size()) {
                return;
            }

            Town.PlayerDebt debt = debts.get(slotIndex);
            handleDebtPayment(player, town, debt);
        }
    }

    /**
     * Gère le paiement d'une dette
     */
    private void handleDebtPayment(Player player, Town town, Town.PlayerDebt debt) {
        Plot plot = debt.plot();
        boolean isCompanyDebt = (plot.getCompanyDebtAmount() > 0);
        double debtAmount = debt.amount();

        player.closeInventory();

        if (isCompanyDebt) {
            // Dette d'entreprise
            String companySiret = plot.getCompanySiret();
            if (companySiret == null) {
                player.sendMessage(ChatColor.RED + "❌ Erreur: SIRET introuvable.");
                return;
            }

            CompanyPlotManager companyManager = plugin.getCompanyPlotManager();
            Entreprise company = companyManager.getCompanyBySiret(companySiret);

            if (company == null) {
                player.sendMessage(ChatColor.RED + "❌ Erreur: Entreprise introuvable.");
                return;
            }

            // Vérifier le solde de l'entreprise
            if (company.getSolde() < debtAmount) {
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "❌ L'entreprise " + company.getNom() + " n'a pas assez d'argent!");
                player.sendMessage(ChatColor.YELLOW + "Dette: " + ChatColor.GOLD + String.format("%.2f€", debtAmount));
                player.sendMessage(ChatColor.YELLOW + "Solde entreprise: " + ChatColor.GOLD + String.format("%.2f€", company.getSolde()));
                player.sendMessage("");
                player.sendMessage(ChatColor.GRAY + "💡 Pour déposer de l'argent:");
                player.sendMessage(ChatColor.GRAY + "  1. Tapez /entreprise");
                player.sendMessage(ChatColor.GRAY + "  2. Cliquez sur 'Mes Entreprises'");
                player.sendMessage(ChatColor.GRAY + "  3. Sélectionnez '" + company.getNom() + "'");
                player.sendMessage(ChatColor.GRAY + "  4. Cliquez sur 'Déposer Argent'");
                player.sendMessage("");
                return;
            }

            // Prélever de l'entreprise
            company.setSolde(company.getSolde() - debtAmount);
            town.deposit(debtAmount);

            // Réinitialiser la dette
            plot.resetDebt();

            // Messages de succès
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ DETTE PAYÉE");
            player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + company.getNom());
            player.sendMessage(ChatColor.YELLOW + "Montant payé: " + ChatColor.GOLD + String.format("%.2f€", debtAmount));
            player.sendMessage(ChatColor.YELLOW + "Solde restant: " + ChatColor.GOLD + String.format("%.2f€", company.getSolde()));

            if (plot.isGrouped()) {
                player.sendMessage(ChatColor.GRAY + "Terrain groupé");
            }
            player.sendMessage(ChatColor.GRAY + "Terrain: " + plot.getCoordinates());

            player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("");

        } else {
            // Dette particulier
            if (!RoleplayCity.getEconomy().has(player, debtAmount)) {
                player.sendMessage("");
                player.sendMessage(ChatColor.RED + "❌ Vous n'avez pas assez d'argent!");
                player.sendMessage(ChatColor.YELLOW + "Dette: " + ChatColor.GOLD + String.format("%.2f€", debtAmount));
                player.sendMessage(ChatColor.YELLOW + "Votre argent: " + ChatColor.GOLD +
                    String.format("%.2f€", RoleplayCity.getEconomy().getBalance(player)));
                player.sendMessage("");
                return;
            }

            // Prélever l'argent personnel
            RoleplayCity.getEconomy().withdrawPlayer(player, debtAmount);
            town.deposit(debtAmount);

            // Réinitialiser la dette
            plot.resetParticularDebt();

            // Messages de succès
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ DETTE PAYÉE");
            player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage(ChatColor.YELLOW + "Montant payé: " + ChatColor.GOLD + String.format("%.2f€", debtAmount));
            player.sendMessage(ChatColor.YELLOW + "Argent restant: " + ChatColor.GOLD +
                String.format("%.2f€", RoleplayCity.getEconomy().getBalance(player)));

            if (plot.isGrouped()) {
                player.sendMessage(ChatColor.GRAY + "Terrain groupé");
            }
            player.sendMessage(ChatColor.GRAY + "Terrain: " + plot.getCoordinates());

            player.sendMessage(ChatColor.GREEN + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("");
        }

        // Sauvegarder
        townManager.saveTownsNow();
        debtNotificationService.refresh(player.getUniqueId(), com.gravityyfh.roleplaycity.town.manager.DebtNotificationService.DebtUpdateReason.PAYMENT);

        // Rouvrir le menu s'il reste des dettes
        List<Town.PlayerDebt> remainingDebts = town.getPlayerDebts(player.getUniqueId());
        if (!remainingDebts.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> openDebtMenu(player, town.getName()), 5L);
        } else {
            player.sendMessage(ChatColor.GREEN + "✓ Toutes vos dettes sont payées !");
        }
    }

    /**
     * Trouve la ville du joueur qui a des dettes
     */
    private String findPlayerTownWithDebts(Player player) {
        for (String townName : townManager.getTownNames()) {
            Town town = townManager.getTown(townName);
            if (town != null && town.isMember(player.getUniqueId())) {
                if (town.hasPlayerDebts(player.getUniqueId())) {
                    return townName;
                }
            }
        }
        return null;
    }
}
