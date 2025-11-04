package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
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
import java.util.UUID;

/**
 * GUI pour afficher et gérer les propriétés d'un joueur
 * (terrains possédés + terrains loués)
 */
public class MyPropertyGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownMainGUI mainGUI;

    public MyPropertyGUI(RoleplayCity plugin, TownManager townManager, TownMainGUI mainGUI) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.mainGUI = mainGUI;
    }

    /**
     * Ouvre le menu "Mes Propriétés" pour le joueur
     */
    public void openPropertyMenu(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Ville introuvable.");
            return;
        }

        UUID playerUuid = player.getUniqueId();

        // Récupérer tous les terrains possédés
        List<Plot> ownedPlots = new ArrayList<>();
        for (Plot plot : town.getPlots().values()) {
            if (playerUuid.equals(plot.getOwnerUuid())) {
                ownedPlots.add(plot);
            }
        }

        // Récupérer tous les terrains loués
        List<Plot> rentedPlots = new ArrayList<>();
        for (Plot plot : town.getPlots().values()) {
            if (playerUuid.equals(plot.getRenterUuid())) {
                rentedPlots.add(plot);
            }
        }

        // Calculer la taille de l'inventaire (multiple de 9)
        int totalPlots = ownedPlots.size() + rentedPlots.size();
        int rows = Math.min(6, Math.max(3, (totalPlots + 9) / 9)); // Entre 3 et 6 lignes
        int invSize = rows * 9;

        Inventory inv = Bukkit.createInventory(null, invSize, ChatColor.GREEN + "🏠 Mes Propriétés");

        int slot = 0;

        // Ajouter les terrains possédés
        if (!ownedPlots.isEmpty()) {
            for (Plot plot : ownedPlots) {
                if (slot >= invSize - 9) break; // Garder la dernière ligne pour les boutons

                ItemStack item = createPlotItem(plot, false);
                inv.setItem(slot, item);
                slot++;
            }
        }

        // Ajouter les terrains loués
        if (!rentedPlots.isEmpty()) {
            for (Plot plot : rentedPlots) {
                if (slot >= invSize - 9) break;

                ItemStack item = createPlotItem(plot, true);
                inv.setItem(slot, item);
                slot++;
            }
        }

        // Boutons de la dernière ligne
        int lastRow = invSize - 9;

        // Bouton "Regrouper mes terrains" si ≥2 terrains possédés
        if (ownedPlots.size() >= 2) {
            ItemStack groupButton = new ItemStack(Material.CHAIN);
            ItemMeta groupMeta = groupButton.getItemMeta();
            groupMeta.setDisplayName(ChatColor.AQUA + "🔗 Regrouper mes terrains");
            List<String> groupLore = new ArrayList<>();
            groupLore.add(ChatColor.GRAY + "Créer un groupe de terrains adjacents");
            groupLore.add(ChatColor.YELLOW + "Terrains possédés: " + ownedPlots.size());
            groupLore.add("");
            groupLore.add(ChatColor.GREEN + "➜ Cliquez pour gérer les regroupements");
            groupMeta.setLore(groupLore);
            groupButton.setItemMeta(groupMeta);
            inv.setItem(lastRow + 2, groupButton);
        }

        // Bouton "Retour au menu principal"
        ItemStack backButton = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour au menu principal");
        backButton.setItemMeta(backMeta);
        inv.setItem(lastRow + 8, backButton);

        player.openInventory(inv);
    }

    /**
     * Crée un ItemStack représentant un terrain
     */
    private ItemStack createPlotItem(Plot plot, boolean isRented) {
        Material material = isRented ? Material.BLUE_CONCRETE : getMaterialForPlotType(plot.getType());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Titre
        String title = isRented ?
            ChatColor.AQUA + "📦 Terrain Loué" :
            ChatColor.GREEN + "🏠 " + plot.getType().getDisplayName();
        meta.setDisplayName(title);

        // Description
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "─────────────────");
        lore.add(ChatColor.YELLOW + "Position: " + ChatColor.WHITE +
            "X: " + (plot.getChunkX() * 16) + " Z: " + (plot.getChunkZ() * 16));
        lore.add(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + "256m² (1 chunk)");

        if (isRented) {
            lore.add(ChatColor.YELLOW + "Jours restants: " + ChatColor.WHITE + plot.getRentDaysRemaining() + "/30");
            lore.add(ChatColor.YELLOW + "Prix/jour: " + ChatColor.GOLD + String.format("%.2f€", plot.getRentPricePerDay()));
        } else {
            lore.add(ChatColor.YELLOW + "Taxe: " + ChatColor.GOLD + String.format("%.2f€/jour", plot.getDailyTax()));

            if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanyName() != null) {
                lore.add(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + plot.getCompanyName());
                if (plot.getCompanyDebtAmount() > 0) {
                    lore.add(ChatColor.RED + "⚠ Dette: " + String.format("%.2f€", plot.getCompanyDebtAmount()));
                }
            }

            if (plot.isForSale()) {
                lore.add(ChatColor.GREEN + "✓ En vente: " + String.format("%.2f€", plot.getSalePrice()));
            }
            if (plot.isForRent()) {
                lore.add(ChatColor.AQUA + "✓ En location: " + String.format("%.2f€/jour", plot.getRentPricePerDay()));
            }
        }

        lore.add("");
        lore.add(ChatColor.GRAY + "Actions disponibles:");
        lore.add(ChatColor.GREEN + "▶ Clic gauche: " + ChatColor.WHITE + "Gérer");
        lore.add(ChatColor.AQUA + "▶ Clic droit: " + ChatColor.WHITE + "Permissions");
        lore.add(ChatColor.YELLOW + "▶ Shift + Clic: " + ChatColor.WHITE + "Téléporter");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Retourne un matériau adapté au type de terrain
     */
    private Material getMaterialForPlotType(PlotType type) {
        return switch (type) {
            case PARTICULIER -> Material.GREEN_CONCRETE;
            case PROFESSIONNEL -> Material.PURPLE_CONCRETE;
            case MUNICIPAL -> Material.YELLOW_CONCRETE;
            case PUBLIC -> Material.WHITE_CONCRETE;
        };
    }

    /**
     * Gère les clics dans le menu
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(ChatColor.GREEN + "🏠 Mes Propriétés")) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) return;

        Town town = townManager.getTown(townName);
        if (town == null) return;

        // Bouton "Retour"
        if (clicked.getType() == Material.ARROW) {
            player.closeInventory();
            mainGUI.openMainMenu(player);
            return;
        }

        // Bouton "Regrouper"
        if (clicked.getType() == Material.CHAIN) {
            player.closeInventory();
            plugin.getPlotGroupManagementGUI().openMainMenu(player, townName);
            return;
        }

        // Clic sur un terrain
        if (isPlotItem(clicked.getType())) {
            Plot plot = findPlotFromItem(clicked, town, player.getUniqueId());
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Terrain introuvable.");
                return;
            }

            boolean isShiftClick = event.isShiftClick();
            boolean isRightClick = event.isRightClick();

            if (isShiftClick) {
                // Téléportation seule
                player.closeInventory();
                teleportToPlot(player, plot);
            } else if (isRightClick) {
                // Téléporter puis ouvrir le menu Permissions
                player.closeInventory();
                teleportToPlot(player, plot);
                // Ouvrir le menu après la téléportation
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getPlotOwnerGUI().openOwnerMenu(player);
                }, 5L);
            } else {
                // Téléporter puis ouvrir le menu Gestion
                player.closeInventory();
                teleportToPlot(player, plot);
                // Ouvrir le menu après la téléportation
                org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    plugin.getTownPlotManagementGUI().openPlotMenu(player);
                }, 5L);
            }
        }
    }

    /**
     * Vérifie si l'item est un terrain
     */
    private boolean isPlotItem(Material material) {
        return material == Material.GREEN_CONCRETE ||
               material == Material.PURPLE_CONCRETE ||
               material == Material.YELLOW_CONCRETE ||
               material == Material.WHITE_CONCRETE ||
               material == Material.BLUE_CONCRETE;
    }

    /**
     * Trouve le terrain correspondant à l'item cliqué
     */
    private Plot findPlotFromItem(ItemStack item, Town town, UUID playerUuid) {
        String displayName = item.getItemMeta().getDisplayName();
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null || lore.size() < 2) return null;

        // Extraire les coordonnées de la lore (ligne 2: "Position: X: 320 Z: -480")
        String posLine = ChatColor.stripColor(lore.get(1));
        try {
            String[] parts = posLine.split(":");
            if (parts.length < 3) return null;

            int x = Integer.parseInt(parts[1].trim().split(" ")[0]);
            int z = Integer.parseInt(parts[2].trim());

            int chunkX = x / 16;
            int chunkZ = z / 16;

            // Chercher le terrain
            for (Plot plot : town.getPlots().values()) {
                if (plot.getChunkX() == chunkX && plot.getChunkZ() == chunkZ) {
                    // Vérifier que c'est bien un terrain du joueur
                    if (playerUuid.equals(plot.getOwnerUuid()) || playerUuid.equals(plot.getRenterUuid())) {
                        return plot;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur lors de la récupération du terrain: " + e.getMessage());
        }
        return null;
    }

    /**
     * Téléporte le joueur sur un terrain
     */
    private void teleportToPlot(Player player, Plot plot) {
        org.bukkit.World world = Bukkit.getWorld(plot.getWorldName());
        if (world == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Monde introuvable.");
            return;
        }

        // Téléporter au centre du chunk, en hauteur sécurisée
        int centerX = plot.getChunkX() * 16 + 8;
        int centerZ = plot.getChunkZ() * 16 + 8;
        int safeY = world.getHighestBlockYAt(centerX, centerZ) + 1;

        Location tpLoc = new Location(world, centerX + 0.5, safeY, centerZ + 0.5);
        player.teleport(tpLoc);
        player.sendMessage(ChatColor.GREEN + "✓ Téléporté sur votre terrain !");
        player.sendMessage(ChatColor.GRAY + "Position: X: " + centerX + " Z: " + centerZ);
    }
}
