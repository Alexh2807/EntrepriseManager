package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.TownEconomyManager;
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

import java.util.ArrayList;
import java.util.List;

/**
 * GUI pour gérer les propriétés louées (recharger, résilier, acheter)
 */
public class RentedPropertyGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownEconomyManager economyManager;
    private final MyPropertyGUI myPropertyGUI;

    public RentedPropertyGUI(RoleplayCity plugin, TownManager townManager, MyPropertyGUI myPropertyGUI) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.economyManager = plugin.getTownEconomyManager();
        this.myPropertyGUI = myPropertyGUI;
    }

    /**
     * Ouvre le menu de gestion d'une propriété louée
     *
     * @param player    Le joueur
     * @param townName  Le nom de la ville
     * @param chunkKey  La clé du chunk "chunkX:chunkZ:worldName"
     */
    public void openRentedManagementMenu(Player player, String townName, String chunkKey) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, "Gestion de Location");

        // Récupérer le plot - Format: "chunkX:chunkZ:worldName"
        String[] parts = chunkKey.split(":");
        Plot plot = town.getPlot(parts[2], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));

        if (plot == null || !player.getUniqueId().equals(plot.getRenterUuid())) {
            player.sendMessage(ChatColor.RED + "Terrain loué introuvable.");
            return;
        }

        // Récupérer les données
        String propertyName;
        int surface;

        if (plot.isGrouped()) {
            propertyName = "Terrain groupé";
            surface = plot.getChunks().size() * 256;
        } else {
            propertyName = "Parcelle " + plot.getChunkX() + ", " + plot.getChunkZ();
            surface = 256;
        }

        double rentPrice = plot.getRentPricePerDay();
        int daysRemaining = plot.getRentDaysRemaining();
        boolean forSale = plot.isForSale();
        double salePrice = plot.getSalePrice();

        // 📅 Obtenir le temps restant détaillé
        Plot.RentTimeRemaining timeRemaining = plot.getRentTimeRemaining();
        String timeDisplay = timeRemaining != null
            ? timeRemaining.formatDetailed()
            : daysRemaining + " jours";

        // BOUTON: Informations
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.YELLOW + "ℹ " + propertyName);
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "─────────────────");
        infoLore.add(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + surface + "m²");
        infoLore.add(ChatColor.YELLOW + "Prix/jour: " + ChatColor.GOLD + String.format("%.2f€", rentPrice));
        infoLore.add(ChatColor.YELLOW + "Temps restant:");
        infoLore.add(ChatColor.WHITE + "  " + timeDisplay);
        infoLore.add(ChatColor.GRAY + "(Max: 30 jours)");
        infoLore.add(ChatColor.GRAY + "─────────────────");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // BOUTON: Recharger 1 jour
        int maxRecharge = 30 - daysRemaining;
        ItemStack rechargeItem = new ItemStack(maxRecharge > 0 ? Material.EMERALD : Material.BARRIER);
        ItemMeta rechargeMeta = rechargeItem.getItemMeta();
        if (maxRecharge > 0) {
            rechargeMeta.setDisplayName(ChatColor.GREEN + "➕ Recharger 1 jour");
            List<String> rechargeLore = new ArrayList<>();
            rechargeLore.add(ChatColor.GRAY + "Ajouter 1 jour de location");
            rechargeLore.add("");
            rechargeLore.add(ChatColor.YELLOW + "Coût: " + ChatColor.GOLD + String.format("%.2f€", rentPrice));
            rechargeLore.add(ChatColor.YELLOW + "Après recharge: " + ChatColor.WHITE + (daysRemaining + 1) + "/30 jours");
            rechargeLore.add("");
            rechargeLore.add(ChatColor.GREEN + "➜ Cliquez pour recharger");
            rechargeMeta.setLore(rechargeLore);
        } else {
            rechargeMeta.setDisplayName(ChatColor.RED + "✗ Maximum atteint");
            List<String> rechargeLore = new ArrayList<>();
            rechargeLore.add(ChatColor.GRAY + "Vous avez déjà 30 jours");
            rechargeLore.add(ChatColor.RED + "Impossible de recharger plus");
            rechargeMeta.setLore(rechargeLore);
        }
        rechargeItem.setItemMeta(rechargeMeta);
        inv.setItem(11, rechargeItem);

        // BOUTON: Résilier
        ItemStack cancelItem = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "🚫 Résilier la location");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add(ChatColor.GRAY + "Arrêter de louer ce terrain");
        cancelLore.add("");
        cancelLore.add(ChatColor.RED + "⚠ Attention:");
        cancelLore.add(ChatColor.GRAY + "Les jours restants seront perdus");
        cancelLore.add(ChatColor.GRAY + "Aucun remboursement");
        cancelLore.add("");
        cancelLore.add(ChatColor.YELLOW + "➜ Cliquez pour résilier");
        cancelMeta.setLore(cancelLore);
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(13, cancelItem);

        // BOUTON: Gérer la boîte aux lettres
        boolean hasMailbox = plot.hasMailbox();
        ItemStack mailboxItem = new ItemStack(hasMailbox ? Material.LIME_CONCRETE : Material.RED_CONCRETE);
        ItemMeta mailboxMeta = mailboxItem.getItemMeta();
        mailboxMeta.setDisplayName(ChatColor.AQUA + "📬 Gestion Boîte aux Lettres");
        List<String> mailboxLore = new ArrayList<>();
        mailboxLore.add(ChatColor.GRAY + "Gérer votre boîte aux lettres");
        mailboxLore.add("");

        if (hasMailbox) {
            mailboxLore.add(ChatColor.GREEN + "✓ Boîte aux lettres installée");
            mailboxLore.add("");
            mailboxLore.add(ChatColor.YELLOW + "Clic gauche: Supprimer");
            mailboxLore.add(ChatColor.YELLOW + "Clic droit: Déplacer");
        } else {
            mailboxLore.add(ChatColor.RED + "✖ Aucune boîte aux lettres");
            mailboxLore.add("");
            mailboxLore.add(ChatColor.YELLOW + "Cliquez pour placer");
        }

        mailboxMeta.setLore(mailboxLore);
        mailboxItem.setItemMeta(mailboxMeta);
        inv.setItem(15, mailboxItem);

        // BOUTON: Acheter (si disponible à la vente)
        if (forSale) {
            ItemStack buyItem = new ItemStack(Material.DIAMOND);
            ItemMeta buyMeta = buyItem.getItemMeta();
            buyMeta.setDisplayName(ChatColor.GREEN + "💰 Acheter le terrain");
            List<String> buyLore = new ArrayList<>();
            buyLore.add(ChatColor.GRAY + "Devenir propriétaire");
            buyLore.add("");
            buyLore.add(ChatColor.YELLOW + "Prix: " + ChatColor.GOLD + String.format("%.2f€", salePrice));
            buyLore.add("");
            buyLore.add(ChatColor.GREEN + "➜ Cliquez pour acheter");
            buyMeta.setLore(buyLore);
            buyItem.setItemMeta(buyMeta);
            inv.setItem(17, buyItem);
        }

        // BOUTON: Retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour à Mes Propriétés");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        // Sauvegarder les données dans la session pour les actions
        player.setMetadata("rentedProperty_townName", new org.bukkit.metadata.FixedMetadataValue(plugin, townName));
        player.setMetadata("rentedProperty_chunkKey", new org.bukkit.metadata.FixedMetadataValue(plugin, chunkKey));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("Gestion de Location")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Récupérer les données de session
        if (!player.hasMetadata("rentedProperty_townName")) return;
        String townName = player.getMetadata("rentedProperty_townName").get(0).asString();
        String chunkKey = player.getMetadata("rentedProperty_chunkKey").get(0).asString();

        Town town = townManager.getTown(townName);
        if (town == null) return;

        // BOUTON: Retour
        if (clicked.getType() == Material.ARROW) {
            player.closeInventory();
            player.removeMetadata("rentedProperty_townName", plugin);
            player.removeMetadata("rentedProperty_chunkKey", plugin);
            myPropertyGUI.openPropertyMenu(player, townName);
            return;
        }

        // BOUTON: Recharger
        if (clicked.getType() == Material.EMERALD) {
            player.closeInventory();
            handleRecharge(player, town, chunkKey);
            return;
        }

        // BOUTON: Résilier
        if (clicked.getType() == Material.RED_CONCRETE) {
            player.closeInventory();
            handleCancel(player, town, chunkKey);
            return;
        }

        // BOUTON: Acheter
        if (clicked.getType() == Material.DIAMOND) {
            player.closeInventory();
            handleBuy(player, town, chunkKey);
            return;
        }

        // BOUTON: Boîte aux lettres
        if (clicked.getType() == Material.LIME_CONCRETE || clicked.getType() == Material.RED_CONCRETE) {
            if (clicked.getItemMeta().getDisplayName().contains("Gestion Boîte aux Lettres")) {
                player.closeInventory();
                handleMailbox(player, town, chunkKey, event.getClick());
            }
        }
    }

    private void handleRecharge(Player player, Town town, String chunkKey) {
        String[] parts = chunkKey.split(":");
        Plot plot = town.getPlot(parts[2], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));

        if (plot == null || !player.getUniqueId().equals(plot.getRenterUuid())) {
            player.sendMessage(ChatColor.RED + "Erreur: Terrain loué introuvable.");
            return;
        }

        // CORRECTION: Utiliser TownEconomyManager.rechargePlotRent() au lieu de gérer le paiement ici
        // Cette méthode gère correctement le paiement par entreprise pour les terrains PRO
        boolean success = plugin.getTownEconomyManager().rechargePlotRent(town.getName(), plot, player, 1);

        if (success) {
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "✓ Location rechargée !");

            if (plot.isGrouped()) {
                player.sendMessage(ChatColor.YELLOW + "Terrain: " + ChatColor.WHITE + "Terrain groupé");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Parcelle: " + ChatColor.WHITE + plot.getChunkX() + ", " + plot.getChunkZ());
            }

            player.sendMessage(ChatColor.YELLOW + "Jours ajoutés: " + ChatColor.WHITE + "1");
            player.sendMessage(ChatColor.YELLOW + "Total: " + ChatColor.WHITE + plot.getRentDaysRemaining() + "/30 jours");
            player.sendMessage(ChatColor.YELLOW + "Coût: " + ChatColor.GOLD + String.format("%.2f€", plot.getRentPricePerDay()));
            player.sendMessage("");
        }
        // Les messages d'erreur sont gérés par rechargePlotRent()
    }

    private void handleCancel(Player player, Town town, String chunkKey) {
        String[] parts = chunkKey.split(":");
        Plot plot = town.getPlot(parts[2], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));

        if (plot == null || !player.getUniqueId().equals(plot.getRenterUuid())) {
            player.sendMessage(ChatColor.RED + "Erreur: Terrain loué introuvable.");
            return;
        }

        String propertyName;
        if (plot.isGrouped()) {
            propertyName = "Terrain groupé";
        } else {
            propertyName = "Parcelle " + plot.getChunkX() + ", " + plot.getChunkZ();
        }

        plot.clearRenter();

        player.sendMessage("");
        player.sendMessage(ChatColor.YELLOW + "Location résiliée pour: " + ChatColor.WHITE + propertyName);
        player.sendMessage(ChatColor.GRAY + "Vous n'êtes plus locataire de ce terrain.");
        player.sendMessage("");

        townManager.saveTownsNow();
    }

    private void handleBuy(Player player, Town town, String chunkKey) {
        String[] parts = chunkKey.split(":");
        int chunkX = Integer.parseInt(parts[0]);
        int chunkZ = Integer.parseInt(parts[1]);
        String worldName = parts[2];
        Plot plot = town.getPlot(worldName, chunkX, chunkZ);

        if (plot == null || !plot.isForSale()) {
            player.sendMessage(ChatColor.RED + "Ce terrain n'est pas en vente.");
            return;
        }

        // FIX: Utiliser la commande /ville buyplot qui gère automatiquement
        // la sélection d'entreprise pour les terrains PROFESSIONNEL
        player.performCommand("ville buyplot " + chunkX + " " + chunkZ + " " + worldName);
    }

    /**
     * Gère la gestion de mailbox pour le locataire
     * Clic gauche: Supprimer mailbox
     * Clic droit: Déplacer mailbox
     * Clic normal: Placer mailbox
     */
    private void handleMailbox(Player player, Town town, String chunkKey, org.bukkit.event.inventory.ClickType clickType) {
        String[] parts = chunkKey.split(":");
        Plot plot = town.getPlot(parts[2], Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));

        if (plot == null || !player.getUniqueId().equals(plot.getRenterUuid())) {
            player.sendMessage(ChatColor.RED + "Erreur: Terrain loué introuvable.");
            return;
        }

        var mailboxManager = plugin.getMailboxManager();
        boolean hasMailbox = plot.hasMailbox();

        if (hasMailbox) {
            // Mailbox existe déjà
            if (clickType.isLeftClick()) {
                // Supprimer la mailbox
                mailboxManager.removeMailbox(plot);
                player.sendMessage("");
                player.sendMessage(ChatColor.GREEN + "✓ Boîte aux lettres supprimée");
                player.sendMessage(ChatColor.GRAY + "Terrain: " + plot.getIdentifier());
                player.sendMessage("");
            } else if (clickType.isRightClick()) {
                // Déplacer la mailbox
                plugin.getMailboxVisualPlacement().startVisualPlacement(player, plot);
                player.sendMessage(ChatColor.YELLOW + "Mode déplacement de boîte aux lettres activé.");
            }
        } else {
            // Pas de mailbox, placer une nouvelle
            plugin.getMailboxVisualPlacement().startVisualPlacement(player, plot);
        }
    }
}
