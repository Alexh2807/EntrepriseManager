package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotGroup;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.TerritoryEntity;
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

        // Récupérer les groupes appartenant au joueur
        List<com.gravityyfh.roleplaycity.town.data.PlotGroup> ownedGroups = town.getPlayerOwnedGroups(playerUuid);

        // NOUVEAU : Récupérer les groupes loués par le joueur
        List<com.gravityyfh.roleplaycity.town.data.PlotGroup> rentedGroups = new ArrayList<>();
        for (com.gravityyfh.roleplaycity.town.data.PlotGroup group : town.getPlotGroups().values()) {
            if (playerUuid.equals(group.getRenterUuid())) {
                rentedGroups.add(group);
            }
        }

        // ⚠️ NOUVEAU SYSTÈME : town.getPlots() contient SEULEMENT les plots individuels
        // Les plots groupés n'existent plus en tant qu'entités séparées, donc pas besoin de filtrer
        List<Plot> ownedPlots = new ArrayList<>();
        for (Plot plot : town.getPlots().values()) {
            if (playerUuid.equals(plot.getOwnerUuid())) {
                // Vérification robuste : s'assurer que ce n'est pas un groupe
                TerritoryEntity territory = town.getTerritoryAt(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
                if (territory instanceof Plot) {
                    ownedPlots.add(plot);
                }
            }
        }

        // ⚠️ NOUVEAU SYSTÈME : Récupérer tous les terrains loués (plots individuels uniquement)
        List<Plot> rentedPlots = new ArrayList<>();
        for (Plot plot : town.getPlots().values()) {
            if (playerUuid.equals(plot.getRenterUuid())) {
                // Vérification robuste : s'assurer que ce n'est pas un groupe
                TerritoryEntity territory = town.getTerritoryAt(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
                if (territory instanceof Plot) {
                    rentedPlots.add(plot);
                }
            }
        }

        // Calculer la taille de l'inventaire (multiple de 9)
        int totalItems = ownedGroups.size() + ownedPlots.size() + rentedGroups.size() + rentedPlots.size();
        int rows = Math.min(6, Math.max(3, (totalItems + 9) / 9)); // Entre 3 et 6 lignes
        int invSize = rows * 9;

        Inventory inv = Bukkit.createInventory(null, invSize, ChatColor.GREEN + "🏠 Mes Propriétés");

        int slot = 0;

        // Ajouter les groupes de parcelles en premier
        if (!ownedGroups.isEmpty()) {
            for (com.gravityyfh.roleplaycity.town.data.PlotGroup group : ownedGroups) {
                if (slot >= invSize - 9) break; // Garder la dernière ligne pour les boutons

                ItemStack item = createPlotGroupItem(group, town);
                inv.setItem(slot, item);
                slot++;
            }
        }

        // Ajouter les terrains possédés
        if (!ownedPlots.isEmpty()) {
            for (Plot plot : ownedPlots) {
                if (slot >= invSize - 9) break; // Garder la dernière ligne pour les boutons

                ItemStack item = createPlotItem(plot, false, town);
                inv.setItem(slot, item);
                slot++;
            }
        }

        // NOUVEAU : Ajouter les groupes loués
        if (!rentedGroups.isEmpty()) {
            for (com.gravityyfh.roleplaycity.town.data.PlotGroup group : rentedGroups) {
                if (slot >= invSize - 9) break;

                ItemStack item = createRentedGroupItem(group, town);
                inv.setItem(slot, item);
                slot++;
            }
        }

        // Ajouter les terrains loués
        if (!rentedPlots.isEmpty()) {
            for (Plot plot : rentedPlots) {
                if (slot >= invSize - 9) break;

                ItemStack item = createPlotItem(plot, true, town);
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
    private ItemStack createPlotItem(Plot plot, boolean isRented, Town town) {
        // Vérifier si la parcelle fait partie d'un groupe
        com.gravityyfh.roleplaycity.town.data.PlotGroup group = town.findPlotGroupByPlot(plot);
        boolean isInGroup = (group != null);

        // Choisir le matériau : ORANGE si groupé, sinon couleur normale
        Material material;
        if (isInGroup) {
            material = Material.ORANGE_CONCRETE;
        } else {
            material = isRented ? Material.BLUE_CONCRETE : getMaterialForPlotType(plot.getType());
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Titre
        String title;
        if (isInGroup) {
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
        if (isInGroup) {
            lore.add(ChatColor.LIGHT_PURPLE + "🏘️ Groupe: " + ChatColor.WHITE + group.getGroupName());
            lore.add(ChatColor.YELLOW + "Surface totale groupe: " + ChatColor.WHITE + (group.getChunkKeys().size() * 256) + "m²");
            lore.add(ChatColor.GRAY + "─────────────────");
        }

        lore.add(ChatColor.YELLOW + "Chunk: " + ChatColor.WHITE +
            "X: " + plot.getChunkX() + " Z: " + plot.getChunkZ());
        lore.add(ChatColor.YELLOW + "Position bloc: " + ChatColor.WHITE +
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

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un ItemStack représentant un groupe de parcelles
     */
    private ItemStack createPlotGroupItem(com.gravityyfh.roleplaycity.town.data.PlotGroup group, Town town) {
        ItemStack item = new ItemStack(Material.ENDER_CHEST);
        ItemMeta meta = item.getItemMeta();

        // Titre
        String title = ChatColor.LIGHT_PURPLE + "🏘️ " + group.getGroupName();
        meta.setDisplayName(title);

        // Description
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "─────────────────");
        lore.add(ChatColor.YELLOW + "Parcelles: " + ChatColor.WHITE + group.getChunkKeys().size());
        lore.add(ChatColor.YELLOW + "Surface totale: " + ChatColor.WHITE + (group.getChunkKeys().size() * 256) + "m²");

        // ⚠️ NOUVEAU SYSTÈME : Utiliser les propriétés directes du groupe autonome
        // Propriétaire ou Entreprise
        if (group.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && group.getCompanySiret() != null) {
            // Terrain PROFESSIONNEL : afficher entreprise directement du groupe
            com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                .getCompanyBySiret(group.getCompanySiret());
            if (ownerCompany != null) {
                lore.add(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + ownerCompany.getNom() + ChatColor.GRAY + " (" + ownerCompany.getType() + ")");
            } else {
                lore.add(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + group.getOwnerName());
            }
        } else {
            // Terrain PARTICULIER
            lore.add(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + group.getOwnerName());
        }

        // Statuts de vente/location
        if (group.isForSale()) {
            lore.add(ChatColor.GREEN + "✓ En vente: " + String.format("%.2f€", group.getSalePrice()));
        }
        if (group.isForRent()) {
            lore.add(ChatColor.AQUA + "✓ En location: " + String.format("%.2f€/jour", group.getRentPricePerDay()));
        }

        // Si loué
        if (group.getRenterUuid() != null) {
            lore.add(ChatColor.YELLOW + "Jours restants: " + ChatColor.WHITE + group.getRentDaysRemaining() + "/30");
        }

        lore.add("");
        lore.add(ChatColor.GRAY + "Actions disponibles:");
        lore.add(ChatColor.GREEN + "▶ Clic gauche: " + ChatColor.WHITE + "Gérer le groupe");
        lore.add(ChatColor.AQUA + "▶ Clic droit: " + ChatColor.WHITE + "Voir les parcelles");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un ItemStack représentant un groupe de parcelles LOUÉ
     */
    private ItemStack createRentedGroupItem(com.gravityyfh.roleplaycity.town.data.PlotGroup group, Town town) {
        ItemStack item = new ItemStack(Material.CYAN_SHULKER_BOX);
        ItemMeta meta = item.getItemMeta();

        // Titre avec indicateur de location
        String title = ChatColor.AQUA + "📦 " + group.getGroupName() + " " + ChatColor.GRAY + "(Loué)";
        meta.setDisplayName(title);

        // Description
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "─────────────────");
        lore.add(ChatColor.YELLOW + "Parcelles: " + ChatColor.WHITE + group.getChunkKeys().size());
        lore.add(ChatColor.YELLOW + "Surface totale: " + ChatColor.WHITE + (group.getChunkKeys().size() * 256) + "m²");
        lore.add(ChatColor.GRAY + "─────────────────");

        // ⚠️ NOUVEAU SYSTÈME : Utiliser les propriétés directes du groupe autonome
        // Propriétaire du groupe ou Entreprise
        if (group.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && group.getCompanySiret() != null) {
            // Terrain PROFESSIONNEL : afficher entreprise directement du groupe
            com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                .getCompanyBySiret(group.getCompanySiret());
            if (ownerCompany != null) {
                lore.add(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + ownerCompany.getNom() + ChatColor.GRAY + " (" + ownerCompany.getType() + ")");
            } else {
                lore.add(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + group.getOwnerName());
            }
        } else {
            // Terrain PARTICULIER
            lore.add(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + group.getOwnerName());
        }

        // Infos de location
        lore.add(ChatColor.YELLOW + "Prix/jour: " + ChatColor.GOLD + String.format("%.2f€", group.getRentPricePerDay()));
        lore.add(ChatColor.YELLOW + "Jours restants: " + ChatColor.WHITE + group.getRentDaysRemaining() + "/30");

        // Si aussi en vente
        if (group.isForSale()) {
            lore.add("");
            lore.add(ChatColor.GREEN + "💰 Disponible à l'achat: " + String.format("%.2f€", group.getSalePrice()));
        }

        lore.add("");
        lore.add(ChatColor.GRAY + "Actions disponibles:");
        lore.add(ChatColor.GREEN + "▶ Cliquez: " + ChatColor.WHITE + "Gérer la location");
        lore.add(ChatColor.GRAY + "  (Recharger, Résilier, Acheter...)");

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

        // Clic sur un groupe de parcelles
        if (clicked.getType() == Material.ENDER_CHEST) {
            com.gravityyfh.roleplaycity.town.data.PlotGroup group = findGroupFromItem(clicked, town, player.getUniqueId());
            if (group == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Groupe introuvable.");
                return;
            }

            player.closeInventory();

            // Ouvrir le GUI détaillé pour gérer ce groupe
            plugin.getPlotGroupDetailGUI().openGroupDetailMenu(player, townName, group.getGroupId());
            return;
        }

        // NOUVEAU : Clic sur un groupe loué (CYAN_SHULKER_BOX)
        if (clicked.getType() == Material.CYAN_SHULKER_BOX) {
            com.gravityyfh.roleplaycity.town.data.PlotGroup group = findRentedGroupFromItem(clicked, town, player.getUniqueId());
            if (group == null) {
                player.sendMessage(ChatColor.RED + "Erreur: Groupe loué introuvable.");
                return;
            }

            player.closeInventory();

            // Ouvrir le GUI de gestion de location
            plugin.getRentedPropertyGUI().openRentedManagementMenu(player, townName, group.getGroupId(), true);
            return;
        }

        // Clic sur un terrain
        if (isPlotItem(clicked.getType())) {
            Plot plot = findPlotFromItem(clicked, town, player.getUniqueId());
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
                plugin.getRentedPropertyGUI().openRentedManagementMenu(player, townName, identifier, false);
                return;
            }

            // CORRECTION : Vérifier si le terrain fait partie d'un groupe (ORANGE_CONCRETE)
            if (clicked.getType() == Material.ORANGE_CONCRETE) {
                com.gravityyfh.roleplaycity.town.data.PlotGroup group = town.findPlotGroupByPlot(plot);
                if (group != null) {
                    // Ouvrir le GUI de gestion de groupe au lieu de la parcelle individuelle
                    plugin.getPlotGroupDetailGUI().openGroupDetailMenu(player, townName, group.getGroupId());
                    return;
                }
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
     * Trouve le groupe correspondant à l'item cliqué
     */
    private com.gravityyfh.roleplaycity.town.data.PlotGroup findGroupFromItem(ItemStack item, Town town, UUID playerUuid) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return null;

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        // Format: "🏘️ Nom du groupe" → extraire "Nom du groupe"
        String groupName = displayName.replace("🏘️", "").trim();

        // Chercher le groupe parmi ceux du joueur
        for (com.gravityyfh.roleplaycity.town.data.PlotGroup group : town.getPlayerOwnedGroups(playerUuid)) {
            if (group.getGroupName().equals(groupName)) {
                return group;
            }
        }

        return null;
    }

    /**
     * Trouve le groupe loué correspondant à l'item cliqué
     */
    private com.gravityyfh.roleplaycity.town.data.PlotGroup findRentedGroupFromItem(ItemStack item, Town town, UUID playerUuid) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) return null;

        String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());

        // Format: "📦 Nom du groupe (Loué)" → extraire "Nom du groupe"
        String groupName = displayName.replace("📦", "").replace("(Loué)", "").trim();

        // Chercher le groupe parmi tous les groupes de la ville
        for (com.gravityyfh.roleplaycity.town.data.PlotGroup group : town.getPlotGroups().values()) {
            // Vérifier que le joueur est le locataire actuel
            if (playerUuid.equals(group.getRenterUuid()) && group.getGroupName().equals(groupName)) {
                return group;
            }
        }

        return null;
    }

    /**
     * Trouve le terrain correspondant à l'item cliqué
     */
    private Plot findPlotFromItem(ItemStack item, Town town, UUID playerUuid) {
        String displayName = item.getItemMeta().getDisplayName();
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null || lore.size() < 2) return null;

        // Extraire les coordonnées de chunk de la lore (ligne 2: "Chunk: X: 5 Z: 8")
        String chunkLine = ChatColor.stripColor(lore.get(1));
        try {
            // Format: "Chunk: X: 5 Z: 8"
            // Extraire X et Z directement (ce sont les coordonnées de chunk)
            String[] parts = chunkLine.split(" ");
            int chunkX = -1, chunkZ = -1;

            for (int i = 0; i < parts.length - 1; i++) {
                if (parts[i].equals("X:")) {
                    chunkX = Integer.parseInt(parts[i + 1]);
                } else if (parts[i].equals("Z:")) {
                    chunkZ = Integer.parseInt(parts[i + 1]);
                }
            }

            if (chunkX == -1 || chunkZ == -1) return null;

            // Chercher le terrain directement par coordonnées de chunk
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
            e.printStackTrace();
        }
        return null;
    }

}
