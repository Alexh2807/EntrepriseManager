package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.gui.NavigationManager;
import com.gravityyfh.roleplaycity.town.data.*;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
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

/**
 * GUI pour gérer un groupe de parcelles spécifique en détail
 */
public class PlotGroupDetailGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    // Sessions d'input chat pour renommer
    private final Map<UUID, RenameContext> renameSessions = new HashMap<>();

    // Sessions d'input pour prix de vente/location
    private final Map<UUID, PriceContext> priceSessions = new HashMap<>();

    // Sessions de confirmation pour dégrouper
    private final Map<UUID, DisassembleContext> disassembleSessions = new HashMap<>();

    public PlotGroupDetailGUI(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
    }

    /**
     * Contexte pour renommer un groupe
     */
    private static class RenameContext {
        final String townName;
        final String groupId;
        final long timestamp;

        RenameContext(String townName, String groupId) {
            this.townName = townName;
            this.groupId = groupId;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 60000; // 1 minute
        }
    }

    /**
     * Contexte pour définir un prix
     */
    private static class PriceContext {
        final String townName;
        final String groupId;
        final PriceType type; // SALE ou RENT
        final long timestamp;

        enum PriceType { SALE, RENT }

        PriceContext(String townName, String groupId, PriceType type) {
            this.townName = townName;
            this.groupId = groupId;
            this.type = type;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 60000;
        }
    }

    /**
     * Contexte pour confirmer le dégroupement
     */
    private static class DisassembleContext {
        final String townName;
        final String groupId;
        final long timestamp;

        DisassembleContext(String townName, String groupId) {
            this.townName = townName;
            this.groupId = groupId;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 30000; // 30 secondes
        }
    }

    /**
     * Ouvre le menu de gestion détaillée d'un groupe
     */
    public void openGroupDetailMenu(Player player, String townName, String groupId) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            NavigationManager.sendError(player, "Ville introuvable.");
            return;
        }

        PlotGroup group = town.getPlotGroup(groupId);
        if (group == null) {
            NavigationManager.sendError(player, "Groupe introuvable.");
            return;
        }

        // Vérifier les permissions
        if (!group.isOwnedBy(player.getUniqueId()) && !town.isMayor(player.getUniqueId())) {
            NavigationManager.sendError(player, "Vous n'avez pas la permission de gérer ce groupe.");
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.LIGHT_PURPLE + "🏘️ " + group.getGroupName());

        // === LIGNE 1 : INFORMATIONS DU GROUPE ===

        // ⚠️ NOUVEAU SYSTÈME : Info groupe à partir des propriétés autonomes
        ItemStack infoItem = new ItemStack(Material.BOOK);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Informations du Groupe");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "─────────────────");
        infoLore.add(ChatColor.YELLOW + "Nom: " + ChatColor.WHITE + group.getGroupName());
        infoLore.add(ChatColor.YELLOW + "Chunks: " + ChatColor.WHITE + group.getChunkKeys().size());
        infoLore.add(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + (group.getChunkKeys().size() * 256) + "m²");

        // Afficher le type depuis le groupe directement
        if (group.getType() != null) {
            infoLore.add(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + group.getType().name());
        }

        // Afficher propriétaire ou entreprise selon le type du groupe
        if (group.getOwnerName() != null) {
            if (group.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && group.getCompanySiret() != null) {
                // Terrain PROFESSIONNEL : afficher entreprise directement du groupe
                com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(group.getCompanySiret());
                if (ownerCompany != null) {
                    infoLore.add(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + ownerCompany.getNom() + ChatColor.GRAY + " (" + ownerCompany.getType() + ")");
                } else {
                    infoLore.add(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + group.getOwnerName());
                }
            } else {
                // Terrain PARTICULIER
                infoLore.add(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + group.getOwnerName());
            }
        }

        // Afficher les dettes si existantes
        double companyDebt = group.getCompanyDebtAmount();
        double particularDebt = group.getParticularDebtAmount();
        if (companyDebt > 0) {
            infoLore.add(ChatColor.RED + "Dette entreprise: " + String.format("%.2f€", companyDebt));
        }
        if (particularDebt > 0) {
            infoLore.add(ChatColor.RED + "Dette particulier: " + String.format("%.2f€", particularDebt));
        }

        infoLore.add(ChatColor.GRAY + "─────────────────");
        if (group.isForSale()) {
            infoLore.add(ChatColor.GREEN + "✓ En vente: " + String.format("%.2f€", group.getSalePrice()));
        }
        if (group.isForRent()) {
            infoLore.add(ChatColor.AQUA + "✓ En location: " + String.format("%.2f€/jour", group.getRentPricePerDay()));
        }
        if (group.getRenterUuid() != null) {
            // Afficher locataire ou entreprise locataire depuis le groupe
            if (group.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && group.getRenterCompanySiret() != null) {
                // Entreprise locataire
                com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise renterCompany = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(group.getRenterCompanySiret());
                if (renterCompany != null) {
                    infoLore.add(ChatColor.LIGHT_PURPLE + "Loué à: " + renterCompany.getNom() + ChatColor.GRAY + " (" + renterCompany.getType() + ") " + ChatColor.WHITE + "(" + group.getRentDaysRemaining() + "j)");
                } else {
                    String renterName = org.bukkit.Bukkit.getOfflinePlayer(group.getRenterUuid()).getName();
                    infoLore.add(ChatColor.LIGHT_PURPLE + "Loué à: " + renterName + " (" + group.getRentDaysRemaining() + "j)");
                }
            } else {
                // Joueur locataire
                String renterName = org.bukkit.Bukkit.getOfflinePlayer(group.getRenterUuid()).getName();
                infoLore.add(ChatColor.LIGHT_PURPLE + "Loué à: " + renterName + " (" + group.getRentDaysRemaining() + "j)");
            }
        }
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // === LIGNE 2 : ACTIONS PRINCIPALES ===

        // Renommer (slot 10)
        ItemStack renameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = renameItem.getItemMeta();
        renameMeta.setDisplayName(ChatColor.AQUA + "Renommer le Groupe");
        List<String> renameLore = new ArrayList<>();
        renameLore.add(ChatColor.GRAY + "Nom actuel: " + ChatColor.WHITE + group.getGroupName());
        renameLore.add("");
        renameLore.add(ChatColor.YELLOW + "Cliquez pour définir un nouveau nom");
        renameMeta.setLore(renameLore);
        renameItem.setItemMeta(renameMeta);
        inv.setItem(10, renameItem);

        // Voir parcelles (slot 13)
        ItemStack plotsItem = new ItemStack(Material.MAP);
        ItemMeta plotsMeta = plotsItem.getItemMeta();
        plotsMeta.setDisplayName(ChatColor.GREEN + "Voir les Parcelles");
        List<String> plotsLore = new ArrayList<>();
        plotsLore.add(ChatColor.GRAY + "Liste des " + group.getChunkKeys().size() + " parcelles");
        plotsLore.add("");
        plotsLore.add(ChatColor.YELLOW + "Cliquez pour voir le détail");
        plotsMeta.setLore(plotsLore);
        plotsItem.setItemMeta(plotsMeta);
        inv.setItem(13, plotsItem);

        // Dégrouper (slot 16) - UNIQUEMENT pour MAIRE et ADJOINT
        TownRole playerRole = town.getMemberRole(player.getUniqueId());
        if (playerRole == TownRole.MAIRE || playerRole == TownRole.ADJOINT) {
            ItemStack disassembleItem = new ItemStack(Material.SHEARS);
            ItemMeta disassembleMeta = disassembleItem.getItemMeta();
            disassembleMeta.setDisplayName(ChatColor.RED + "Dégrouper les Parcelles");
            List<String> disassembleLore = new ArrayList<>();
            disassembleLore.add(ChatColor.GRAY + "Dissoudre ce groupe et");
            disassembleLore.add(ChatColor.GRAY + "séparer toutes les parcelles");
            disassembleLore.add("");
            disassembleLore.add(ChatColor.RED + "⚠ Action irréversible !");
            disassembleLore.add("");
            disassembleLore.add(ChatColor.YELLOW + "Cliquez pour dégrouper");
            disassembleMeta.setLore(disassembleLore);
            disassembleItem.setItemMeta(disassembleMeta);
            inv.setItem(16, disassembleItem);
        }

        // === LIGNE 3 : GESTION ÉCONOMIQUE ===

        // Mettre en vente (slot 19)
        ItemStack saleItem = new ItemStack(group.isForSale() ? Material.LIME_DYE : Material.EMERALD);
        ItemMeta saleMeta = saleItem.getItemMeta();
        if (group.isForSale()) {
            saleMeta.setDisplayName(ChatColor.GREEN + "En Vente");
            List<String> saleLore = new ArrayList<>();
            saleLore.add(ChatColor.GRAY + "Prix: " + ChatColor.GOLD + String.format("%.2f€", group.getSalePrice()));
            saleLore.add("");
            saleLore.add(ChatColor.YELLOW + "Clic gauche: Changer le prix");
            saleLore.add(ChatColor.RED + "Clic droit: Annuler la vente");
            saleMeta.setLore(saleLore);
        } else {
            saleMeta.setDisplayName(ChatColor.GRAY + "Mettre en Vente");
            List<String> saleLore = new ArrayList<>();
            saleLore.add(ChatColor.GRAY + "Vendre tout le groupe d'un coup");
            saleLore.add("");
            saleLore.add(ChatColor.YELLOW + "Cliquez pour définir un prix");
            saleMeta.setLore(saleLore);
        }
        saleItem.setItemMeta(saleMeta);
        inv.setItem(19, saleItem);

        // Mettre en location (slot 25)
        ItemStack rentItem = new ItemStack(group.isForRent() ? Material.CYAN_DYE : Material.DIAMOND);
        ItemMeta rentMeta = rentItem.getItemMeta();
        if (group.isForRent()) {
            rentMeta.setDisplayName(ChatColor.AQUA + "En Location");
            List<String> rentLore = new ArrayList<>();
            rentLore.add(ChatColor.GRAY + "Prix: " + ChatColor.GOLD + String.format("%.2f€/jour", group.getRentPricePerDay()));
            if (group.getRenterUuid() != null) {
                String renterName = org.bukkit.Bukkit.getOfflinePlayer(group.getRenterUuid()).getName();
                rentLore.add(ChatColor.YELLOW + "Loué à: " + ChatColor.WHITE + renterName);
                rentLore.add(ChatColor.YELLOW + "Jours restants: " + ChatColor.WHITE + group.getRentDaysRemaining());
            } else {
                rentLore.add(ChatColor.GREEN + "Disponible");
            }
            rentLore.add("");
            rentLore.add(ChatColor.YELLOW + "Clic gauche: Changer le prix");
            rentLore.add(ChatColor.RED + "Clic droit: Annuler la location");
            rentMeta.setLore(rentLore);
        } else {
            rentMeta.setDisplayName(ChatColor.GRAY + "Mettre en Location");
            List<String> rentLore = new ArrayList<>();
            rentLore.add(ChatColor.GRAY + "Louer tout le groupe");
            rentLore.add("");
            rentLore.add(ChatColor.YELLOW + "Cliquez pour définir un prix/jour");
            rentMeta.setLore(rentLore);
        }
        rentItem.setItemMeta(rentMeta);
        inv.setItem(25, rentItem);

        // Retourner à la ville (UNCLAIM) - UNIQUEMENT si le groupe appartient à un joueur (slot 22)
        if (group.getOwnerUuid() != null && group.getRenterUuid() == null) {
            // Le groupe appartient à un joueur ET n'est pas loué
            ItemStack unclaimItem = new ItemStack(Material.BARRIER);
            ItemMeta unclaimMeta = unclaimItem.getItemMeta();
            unclaimMeta.setDisplayName(ChatColor.DARK_RED + "Retourner à la Ville");
            List<String> unclaimLore = new ArrayList<>();
            unclaimLore.add(ChatColor.GRAY + "Rendre ce groupe à la ville");
            unclaimLore.add(ChatColor.GRAY + "Le groupe restera groupé");
            unclaimLore.add(ChatColor.GRAY + "et sera remis en vente");
            unclaimLore.add("");
            unclaimLore.add(ChatColor.DARK_GRAY + "Seul le maire peut dégrouper");
            unclaimLore.add("");
            unclaimLore.add(ChatColor.RED + "Attention: Aucun remboursement");
            unclaimLore.add("");
            unclaimLore.add(ChatColor.YELLOW + "Cliquez pour UNCLAIM");
            unclaimMeta.setLore(unclaimLore);
            unclaimItem.setItemMeta(unclaimMeta);
            inv.setItem(22, unclaimItem);
        }

        // === LIGNE 6 : BOUTON RETOUR ===

        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        backItem.setItemMeta(backMeta);
        inv.setItem(49, backItem);

        player.openInventory(inv);
    }

    /**
     * ⚠️ NOUVEAU SYSTÈME : Affiche les chunks du groupe autonome
     */
    public void openPlotListMenu(Player player, String townName, String groupId) {
        Town town = townManager.getTown(townName);
        if (town == null) return;

        PlotGroup group = town.getPlotGroup(groupId);
        if (group == null) return;

        // ⚠️ NOUVEAU SYSTÈME : Récupérer les chunks du groupe directement
        Set<String> chunkKeys = group.getChunkKeys();
        int rows = Math.min(6, Math.max(2, (chunkKeys.size() + 9) / 9));
        Inventory inv = Bukkit.createInventory(null, rows * 9, ChatColor.GREEN + "Chunks: " + group.getGroupName());

        int slot = 0;
        for (String chunkKey : chunkKeys) {
            if (slot >= (rows - 1) * 9) break;

            ItemStack item = createChunkDisplayItem(chunkKey, group);
            inv.setItem(slot, item);
            slot++;
        }

        // Bouton retour
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour au groupe");
        backItem.setItemMeta(backMeta);
        inv.setItem(inv.getSize() - 1, backItem);

        player.openInventory(inv);
    }

    /**
     * ⚠️ NOUVEAU SYSTÈME : Crée un item représentant un chunk du groupe
     */
    private ItemStack createChunkDisplayItem(String chunkKey, PlotGroup group) {
        Material material = getMaterialForPlotType(group.getType() != null ? group.getType() : PlotType.PARTICULIER);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String[] parts = chunkKey.split(":");
        if (parts.length == 3) {
            int chunkX = Integer.parseInt(parts[1]);
            int chunkZ = Integer.parseInt(parts[2]);

            meta.setDisplayName(ChatColor.GREEN + (group.getType() != null ? group.getType().getDisplayName() : "Chunk"));

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "─────────────────");
            lore.add(ChatColor.YELLOW + "Chunk: " + ChatColor.WHITE + "X:" + chunkX + " Z:" + chunkZ);
            lore.add(ChatColor.YELLOW + "Position: " + ChatColor.WHITE + "X:" + (chunkX * 16) + " Z:" + (chunkZ * 16));
            lore.add(ChatColor.YELLOW + "Monde: " + ChatColor.WHITE + parts[0]);

            if (group.getCompanyName() != null) {
                lore.add(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + group.getCompanyName());
            }

            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    /**
     * @deprecated Ne plus utiliser - plots individuels n'existent plus dans les groupes
     */
    @Deprecated
    private ItemStack createPlotDisplayItem(Plot plot) {
        Material material = getMaterialForPlotType(plot.getType());
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GREEN + plot.getType().getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "─────────────────");
        lore.add(ChatColor.YELLOW + "Chunk: " + ChatColor.WHITE + "X:" + plot.getChunkX() + " Z:" + plot.getChunkZ());
        lore.add(ChatColor.YELLOW + "Position: " + ChatColor.WHITE + "X:" + (plot.getChunkX() * 16) + " Z:" + (plot.getChunkZ() * 16));
        lore.add(ChatColor.YELLOW + "Monde: " + ChatColor.WHITE + plot.getWorldName());

        if (plot.getCompanyName() != null) {
            lore.add(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + plot.getCompanyName());
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private Material getMaterialForPlotType(PlotType type) {
        return switch (type) {
            case PARTICULIER -> Material.GREEN_CONCRETE;
            case PROFESSIONNEL -> Material.PURPLE_CONCRETE;
            case MUNICIPAL -> Material.YELLOW_CONCRETE;
            case PUBLIC -> Material.WHITE_CONCRETE;
        };
    }

    /**
     * Gestion des clics dans les menus
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = event.getView().getTitle();

        // Menu principal de détail du groupe
        if (title.startsWith(ChatColor.LIGHT_PURPLE + "🏘️ ")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            String townName = townManager.getPlayerTown(player.getUniqueId());
            if (townName == null) return;

            Town town = townManager.getTown(townName);
            if (town == null) return;

            // Trouver le groupe par le nom du menu
            String groupName = ChatColor.stripColor(title).replace("🏘️ ", "").trim();
            PlotGroup group = findGroupByName(town, groupName);
            if (group == null) {
                NavigationManager.sendError(player, "Groupe introuvable.");
                player.closeInventory();
                return;
            }

            handleGroupDetailClick(player, town, group, clicked, event.isRightClick());
        }

        // Menu de liste des parcelles
        if (title.startsWith(ChatColor.GREEN + "Parcelles: ")) {
            event.setCancelled(true);

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            // Bouton retour
            if (clicked.getType() == Material.ARROW) {
                String townName = townManager.getPlayerTown(player.getUniqueId());
                if (townName == null) return;

                Town town = townManager.getTown(townName);
                if (town == null) return;

                String groupName = ChatColor.stripColor(title).replace("Parcelles: ", "").trim();
                PlotGroup group = findGroupByName(town, groupName);
                if (group != null) {
                    player.closeInventory();
                    openGroupDetailMenu(player, townName, group.getGroupId());
                }
            }
        }
    }

    /**
     * Gère les clics dans le menu de détail du groupe
     */
    private void handleGroupDetailClick(Player player, Town town, PlotGroup group, ItemStack clicked, boolean isRightClick) {
        String townName = town.getName();
        String groupId = group.getGroupId();

        switch (clicked.getType()) {
            case ARROW: // Retour
                player.closeInventory();
                plugin.getMyPropertyGUI().openPropertyMenu(player, townName);
                break;

            case NAME_TAG: // Renommer
                startRenameProcess(player, townName, groupId);
                break;

            case MAP: // Voir parcelles
                player.closeInventory();
                openPlotListMenu(player, townName, groupId);
                break;

            case SHEARS: // Dégrouper
                startDisassembleProcess(player, townName, groupId);
                break;

            case EMERALD:
            case LIME_DYE: // Vente
                // PROTECTION : Interdire la vente si le groupe est loué
                if (group.getRenterUuid() != null) {
                    NavigationManager.sendError(player, "Impossible de modifier la vente : le groupe est actuellement loué ! Attendez la fin de la location.");
                    return;
                }

                if (isRightClick && group.isForSale()) {
                    // Annuler la vente
                    group.setForSale(false);
                    NavigationManager.sendSuccess(player, "Vente annulée pour le groupe " + group.getGroupName());
                    townManager.saveTownsNow();
                    openGroupDetailMenu(player, townName, groupId);
                } else {
                    // Définir/Modifier prix de vente
                    startSalePriceProcess(player, townName, groupId);
                }
                break;

            case DIAMOND:
            case CYAN_DYE: // Location
                if (isRightClick && group.isForRent()) {
                    // PROTECTION : Interdire l'annulation si quelqu'un loue
                    if (group.getRenterUuid() != null) {
                        String renterName = org.bukkit.Bukkit.getOfflinePlayer(group.getRenterUuid()).getName();
                        NavigationManager.sendStyledMessage(player, "IMPOSSIBLE D'ANNULER LA LOCATION", Arrays.asList(
                            "!Le groupe est actuellement loué !",
                            "",
                            "Locataire: " + renterName,
                            "Jours restants: " + group.getRentDaysRemaining(),
                            "",
                            "Vous devez attendre la fin de la location."
                        ));
                        return;
                    }
                    // Annuler la location
                    group.setForRent(false);
                    NavigationManager.sendSuccess(player, "Location annulée pour le groupe " + group.getGroupName());
                    townManager.saveTownsNow();
                    openGroupDetailMenu(player, townName, groupId);
                } else {
                    // PROTECTION : Interdire la modification du prix si quelqu'un loue
                    if (group.getRenterUuid() != null) {
                        NavigationManager.sendError(player, "Impossible de modifier le prix : le groupe est actuellement loué ! Le prix ne peut être modifié pendant une location active.");
                        return;
                    }
                    // Définir/Modifier prix de location
                    startRentPriceProcess(player, townName, groupId);
                }
                break;

            case BARRIER: // UNCLAIM - Retourner le terrain à la ville
                unclaimGroup(player, town, group);
                break;
        }
    }

    /**
     * Retourne un groupe de terrains à la ville (UNCLAIM)
     */
    private void unclaimGroup(Player player, Town town, PlotGroup group) {
        // Vérifier si le groupe est loué
        if (group.getRenterUuid() != null) {
            NavigationManager.sendError(player, "Impossible de retourner ce groupe à la ville : il est actuellement loué !");
            return;
        }

        // Retirer le propriétaire du GROUPE (le groupe reste intact)
        // C'est le maire qui décide du groupement/dégroupement, pas le propriétaire
        // ⚠️ NOUVEAU SYSTÈME : Nettoyer uniquement les propriétés du groupe autonome
        group.setOwner(null, null);
        group.setCompanyName(null);
        group.setCompanySiret(null);
        group.resetDebt();
        group.resetParticularDebt();

        // Mettre le GROUPE en vente
        group.setForSale(true);
        int chunkCount = group.getChunkKeys().size();
        double defaultPrice = chunkCount * 1000.0; // 1000€ par chunk
        group.setSalePrice(defaultPrice);

        // Nettoyer les paramètres de location du groupe
        group.setForRent(false);
        group.clearRenter();

        townManager.saveTownsNow();

        player.closeInventory();
        NavigationManager.sendStyledMessage(player, "GROUPE RETOURNE A LA VILLE", Arrays.asList(
            "+Le groupe '" + group.getGroupName() + "' a été retourné à la ville",
            "",
            "Parcelles: " + group.getChunkKeys().size(),
            "*Le groupe reste groupé (seul le maire peut dégrouper)",
            "*Le groupe est maintenant en vente",
            "*Prix: " + String.format("%.2f€", defaultPrice)
        ));
    }

    /**
     * Démarre le processus de renommage
     */
    private void startRenameProcess(Player player, String townName, String groupId) {
        player.closeInventory();

        renameSessions.put(player.getUniqueId(), new RenameContext(townName, groupId));

        NavigationManager.sendStyledMessage(player, "✏ RENOMMER LE GROUPE", Arrays.asList(
            "Entrez le nouveau nom dans le chat",
            "",
            "*Le nom doit contenir entre 3 et 30 caractères",
            "*Lettres, chiffres et espaces autorisés",
            "",
            "Tapez 'annuler' pour annuler l'opération"
        ));
    }

    /**
     * Démarre le processus de définition de prix de vente
     */
    private void startSalePriceProcess(Player player, String townName, String groupId) {
        player.closeInventory();

        priceSessions.put(player.getUniqueId(), new PriceContext(townName, groupId, PriceContext.PriceType.SALE));

        NavigationManager.sendStyledMessage(player, "💰 PRIX DE VENTE DU GROUPE", Arrays.asList(
            "Entrez le prix de vente dans le chat",
            "",
            "*Le prix doit être supérieur à 0",
            "*Format: nombre (ex: 1000 ou 1000.50)",
            "",
            "Tapez 'annuler' pour annuler l'opération"
        ));
    }

    /**
     * Démarre le processus de définition de prix de location
     */
    private void startRentPriceProcess(Player player, String townName, String groupId) {
        player.closeInventory();

        priceSessions.put(player.getUniqueId(), new PriceContext(townName, groupId, PriceContext.PriceType.RENT));

        NavigationManager.sendStyledMessage(player, "🏠 PRIX DE LOCATION DU GROUPE", Arrays.asList(
            "Entrez le prix par jour dans le chat",
            "",
            "*Le prix doit être supérieur à 0",
            "*Format: nombre (ex: 50 ou 50.25)",
            "*Ce montant sera payé chaque jour par le locataire",
            "",
            "Tapez 'annuler' pour annuler l'opération"
        ));
    }

    /**
     * Démarre le processus de dégroupement
     */
    private void startDisassembleProcess(Player player, String townName, String groupId) {
        player.closeInventory();

        Town town = townManager.getTown(townName);
        PlotGroup group = town != null ? town.getPlotGroup(groupId) : null;
        if (group == null) return;

        // VÉRIFICATION : Seuls le Maire et l'Adjoint peuvent dégrouper
        TownRole role = town.getMemberRole(player.getUniqueId());
        if (role == null || (role != TownRole.MAIRE && role != TownRole.ADJOINT)) {
            NavigationManager.sendError(player, "Seuls le Maire et l'Adjoint peuvent dégrouper des parcelles.");
            return;
        }

        // VÉRIFICATION : Interdire le dégroupement si le groupe est loué
        if (group.getRenterUuid() != null) {
            String renterName = org.bukkit.Bukkit.getOfflinePlayer(group.getRenterUuid()).getName();
            NavigationManager.sendStyledMessage(player, "⚠ IMPOSSIBLE DE DÉGROUPER", Arrays.asList(
                "!Ce groupe est actuellement loué !",
                "",
                "Locataire: " + renterName,
                "Jours restants: " + group.getRentDaysRemaining(),
                "",
                "Vous devez attendre la fin de la location",
                "ou contacter le locataire pour un accord."
            ));
            return;
        }

        disassembleSessions.put(player.getUniqueId(), new DisassembleContext(townName, groupId));

        NavigationManager.sendStyledMessage(player, "⚠ DÉGROUPEMENT DE PARCELLES", Arrays.asList(
            "!Cette action est IRRÉVERSIBLE !",
            "",
            "Groupe: " + group.getGroupName(),
            "Parcelles: " + group.getChunkKeys().size(),
            "",
            "*Les parcelles redeviendront indépendantes",
            "*Tous les paramètres du groupe seront perdus",
            "",
            "Tapez 'confirmer' pour dégrouper",
            "Tapez 'annuler' pour annuler"
        ));
    }

    /**
     * Écoute les messages du chat pour les inputs
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String message = event.getMessage().trim();

        // Gestion du renommage
        if (renameSessions.containsKey(playerUuid)) {
            event.setCancelled(true);
            RenameContext context = renameSessions.remove(playerUuid);

            if (context.isExpired()) {
                NavigationManager.sendError(player, "Session expirée. Veuillez réessayer.");
                return;
            }

            if (message.equalsIgnoreCase("annuler")) {
                NavigationManager.sendInfo(player, "OPÉRATION ANNULÉE", "Renommage annulé.");
                return;
            }

            // Valider le nom (3-30 caractères, lettres/chiffres/espaces)
            if (message.length() < 3 || message.length() > 30) {
                NavigationManager.sendError(player, "Le nom doit contenir entre 3 et 30 caractères.");
                return;
            }

            Town town = townManager.getTown(context.townName);
            PlotGroup group = town != null ? town.getPlotGroup(context.groupId) : null;

            if (group == null) {
                NavigationManager.sendError(player, "Groupe introuvable.");
                return;
            }

            String oldName = group.getGroupName();
            group.setGroupName(message);
            townManager.saveTownsNow();

            NavigationManager.sendStyledMessage(player, "✓ GROUPE RENOMMÉ", Arrays.asList(
                "+Renommage effectué avec succès !",
                "",
                "Ancien nom: " + oldName,
                "Nouveau nom: " + message
            ));

            // Rouvrir le menu
            Bukkit.getScheduler().runTask(plugin, () ->
                openGroupDetailMenu(player, context.townName, context.groupId)
            );
        }

        // Gestion des prix
        if (priceSessions.containsKey(playerUuid)) {
            event.setCancelled(true);
            PriceContext context = priceSessions.remove(playerUuid);

            if (context.isExpired()) {
                NavigationManager.sendError(player, "Session expirée. Veuillez réessayer.");
                return;
            }

            if (message.equalsIgnoreCase("annuler")) {
                NavigationManager.sendInfo(player, "OPÉRATION ANNULÉE", "Opération annulée.");
                return;
            }

            double price;
            try {
                price = Double.parseDouble(message.replace(",", "."));
                if (price <= 0) {
                    NavigationManager.sendError(player, "Le prix doit être supérieur à 0.");
                    return;
                }
            } catch (NumberFormatException e) {
                NavigationManager.sendError(player, "Prix invalide. Utilisez un nombre (ex: 1000 ou 1000.50)");
                return;
            }

            Town town = townManager.getTown(context.townName);
            PlotGroup group = town != null ? town.getPlotGroup(context.groupId) : null;

            if (group == null) {
                NavigationManager.sendError(player, "Groupe introuvable.");
                return;
            }

            if (context.type == PriceContext.PriceType.SALE) {
                group.setSalePrice(price);
                group.setForSale(true);
                NavigationManager.sendStyledMessage(player, "✓ GROUPE MIS EN VENTE", Arrays.asList(
                    "+Le groupe est maintenant disponible à l'achat !",
                    "",
                    "Groupe: " + group.getGroupName(),
                    "Prix: " + String.format("%.2f€", price)
                ));
            } else {
                group.setRentPricePerDay(price);
                group.setForRent(true);
                NavigationManager.sendStyledMessage(player, "✓ GROUPE MIS EN LOCATION", Arrays.asList(
                    "+Le groupe est maintenant disponible à la location !",
                    "",
                    "Groupe: " + group.getGroupName(),
                    "Prix: " + String.format("%.2f€/jour", price)
                ));
            }

            townManager.saveTownsNow();

            // Rouvrir le menu
            Bukkit.getScheduler().runTask(plugin, () ->
                openGroupDetailMenu(player, context.townName, context.groupId)
            );
        }

        // Gestion du dégroupement
        if (disassembleSessions.containsKey(playerUuid)) {
            event.setCancelled(true);
            DisassembleContext context = disassembleSessions.remove(playerUuid);

            if (context.isExpired()) {
                NavigationManager.sendError(player, "Session expirée. Veuillez réessayer.");
                return;
            }

            if (message.equalsIgnoreCase("annuler")) {
                NavigationManager.sendInfo(player, "OPÉRATION ANNULÉE", "Dégroupement annulé.");
                return;
            }

            if (!message.equalsIgnoreCase("confirmer")) {
                NavigationManager.sendError(player, "Tapez 'confirmer' pour valider ou 'annuler' pour annuler.");
                return;
            }

            Town town = townManager.getTown(context.townName);
            PlotGroup group = town != null ? town.getPlotGroup(context.groupId) : null;

            if (group == null) {
                NavigationManager.sendError(player, "Groupe introuvable.");
                return;
            }

            // Dégrouper
            int plotCount = group.getChunkKeys().size();
            String groupName = group.getGroupName();

            // ⚠️ CORRECTION : Utiliser dissolveGroup() qui recrée les plots individuels
            List<Plot> createdPlots = town.dissolveGroup(context.groupId);
            townManager.saveTownsNow();

            NavigationManager.sendStyledMessage(player, "✓ GROUPE DÉGROUPÉ AVEC SUCCÈS", Arrays.asList(
                "+Les parcelles ont été séparées !",
                "",
                "Ancien groupe: " + groupName,
                "Parcelles libérées: " + plotCount,
                "",
                "*Les parcelles sont maintenant indépendantes",
                "*Vous pouvez les gérer individuellement"
            ));
        }
    }

    /**
     * Trouve un groupe par son nom dans une ville
     */
    private PlotGroup findGroupByName(Town town, String groupName) {
        for (PlotGroup group : town.getPlotGroups().values()) {
            if (group.getGroupName().equals(groupName)) {
                return group;
            }
        }
        return null;
    }
}
