package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.Town;
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

import java.util.*;

/**
 * GUI pour afficher et gérer les propriétés d'un joueur
 * (terrains possédés + terrains loués)
 */
public class MyPropertyGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownMainGUI mainGUI;

    // Map temporaire : UUID joueur → Map<Slot, Plot>
    // Permet de retrouver directement le Plot quand le joueur clique sur un slot
    private final Map<UUID, Map<Integer, Plot>> playerPlotSlots = new HashMap<>();

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

        // Récupérer les terrains possédés
        List<Plot> ownedPlots = new ArrayList<>();
        for (Plot plot : town.getPlots().values()) {
            if (playerUuid.equals(plot.getOwnerUuid())) {
                ownedPlots.add(plot);
            }
        }

        // Récupérer les terrains loués
        List<Plot> rentedPlots = new ArrayList<>();
        for (Plot plot : town.getPlots().values()) {
            if (playerUuid.equals(plot.getRenterUuid())) {
                rentedPlots.add(plot);
            }
        }

        // Calculer la taille de l'inventaire (multiple de 9)
        int totalItems = ownedPlots.size() + rentedPlots.size();
        int rows = Math.min(6, Math.max(3, (totalItems + 9) / 9)); // Entre 3 et 6 lignes
        int invSize = rows * 9;

        Inventory inv = Bukkit.createInventory(null, invSize, ChatColor.GREEN + "🏠 Mes Propriétés");

        // Map temporaire pour associer slot → Plot
        Map<Integer, Plot> slotMap = new HashMap<>();
        int slot = 0;

        // Ajouter les terrains possédés
        if (!ownedPlots.isEmpty()) {
            for (Plot plot : ownedPlots) {
                if (slot >= invSize - 9) break; // Garder la dernière ligne pour les boutons

                ItemStack item = createPlotItem(plot, false, town);
                inv.setItem(slot, item);
                slotMap.put(slot, plot); // Stocker l'association
                slot++;
            }
        }

        // Ajouter les terrains loués
        if (!rentedPlots.isEmpty()) {
            for (Plot plot : rentedPlots) {
                if (slot >= invSize - 9) break;

                ItemStack item = createPlotItem(plot, true, town);
                inv.setItem(slot, item);
                slotMap.put(slot, plot); // Stocker l'association
                slot++;
            }
        }

        // Sauvegarder la map pour ce joueur
        playerPlotSlots.put(playerUuid, slotMap);

        // Boutons de la dernière ligne
        int lastRow = invSize - 9;

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
    private ItemStack createPlotItem(Plot plot, boolean isRented, Town town) {
        int totalChunks = plot.getChunks().size();
        boolean isGrouped = plot.isGrouped();

        // Choisir le matériau
        Material material = isRented ? Material.BLUE_CONCRETE :
                           (isGrouped ? Material.ORANGE_CONCRETE : getMaterialForPlotType(plot.getType()));

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Titre
        String title;
        if (isGrouped) {
            title = ChatColor.GOLD + "🔗 " + plot.getType().getDisplayName() + " (Groupé)";
        } else {
            title = isRented ?
                ChatColor.AQUA + "📦 Terrain Loué" :
                ChatColor.GREEN + "🏠 " + plot.getType().getDisplayName();
        }
        meta.setDisplayName(title);

        // Description
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "─────────────────");

        // Si groupé, afficher les infos du groupe
        if (isGrouped) {
            lore.add(ChatColor.LIGHT_PURPLE + "🏘️ Terrain groupé");
            lore.add(ChatColor.YELLOW + "Chunks: " + ChatColor.WHITE + totalChunks);
            lore.add(ChatColor.YELLOW + "Surface totale: " + ChatColor.WHITE + (totalChunks * 256) + "m²");
            lore.add(ChatColor.GRAY + "─────────────────");
        } else {
            lore.add(ChatColor.YELLOW + "Chunk: " + ChatColor.WHITE +
                "X: " + plot.getChunkX() + " Z: " + plot.getChunkZ());
            lore.add(ChatColor.YELLOW + "Position bloc: " + ChatColor.WHITE +
                "X: " + (plot.getChunkX() * 16) + " Z: " + (plot.getChunkZ() * 16));
            lore.add(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + "256m² (1 chunk)");
        }

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

        // Clic sur un terrain
        if (isPlotItem(clicked.getType())) {
            // Récupérer le Plot directement depuis la map
            Map<Integer, Plot> slotMap = playerPlotSlots.get(player.getUniqueId());
            if (slotMap == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Session expirée, réouvrez le menu.");
                player.closeInventory();
                return;
            }

            Plot plot = slotMap.get(event.getSlot());
            if (plot == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Terrain introuvable.");
                return;
            }

            // NOUVEAU : Vérifier si c'est un terrain loué (BLUE_CONCRETE)
            boolean isRented = (clicked.getType() == Material.BLUE_CONCRETE);

            player.closeInventory();

            if (isRented) {
                // Terrain loué → Ouvrir le GUI de gestion de location
                String identifier = plot.getChunkX() + ":" + plot.getChunkZ() + ":" + plot.getWorldName();
                plugin.getRentedPropertyGUI().openRentedManagementMenu(player, townName, identifier);
                return;
            }

            // FIX UX P2.1: Gestion directe depuis "Mes Propriétés" sans vérification de présence physique
            boolean isRightClick = event.isRightClick();

            if (isRightClick) {
                // Ouvrir le menu Permissions
                plugin.getPlotOwnerGUI().openOwnerMenu(player, plot);
            } else {
                // Ouvrir le menu Gestion
                plugin.getTownPlotManagementGUI().openPlotMenu(player, plot);
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
               material == Material.BLUE_CONCRETE ||
               material == Material.ORANGE_CONCRETE; // Parcelles groupées
    }

    /**
     * Nettoie la map quand le joueur ferme l'inventaire
     */
    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(ChatColor.GREEN + "🏠 Mes Propriétés")) return;

        // Nettoyer la map pour libérer la mémoire
        playerPlotSlots.remove(player.getUniqueId());
    }

}
