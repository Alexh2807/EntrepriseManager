package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.data.MunicipalSubType;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;

import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
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

public class TownClaimsGUI implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;
    private TownPlotManagementGUI plotManagementGUI;
    private TownAdminGUI adminGUI;

    private static final String CLAIMS_TITLE = ChatColor.DARK_GREEN + "🗺️ Gestion des Claims";
    private static final String TYPE_SELECTION_TITLE = ChatColor.BLUE + "🏗️ Choisir le Type de Terrain";
    private static final String SUBTYPE_SELECTION_TITLE = ChatColor.DARK_PURPLE + "⚙ Choisir le Sous-Type Municipal";

    // Stockage du plot en cours de configuration après un claim
    private final Map<UUID, Plot> pendingTypePlots = new HashMap<>();

    public TownClaimsGUI(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
    }

    public void setPlotManagementGUI(TownPlotManagementGUI plotManagementGUI) {
        this.plotManagementGUI = plotManagementGUI;
    }

    public void setAdminGUI(TownAdminGUI adminGUI) {
        this.adminGUI = adminGUI;
    }

    /**
     * Vérifie si le joueur possède au moins un terrain dans la ville
     */
    private boolean hasOwnedPlots(Player player, Town town) {
        UUID playerUuid = player.getUniqueId();

        // Vérifier plots
        for (Plot plot : town.getPlots().values()) {
            if (plot.isOwnedBy(playerUuid)) {
                return true;
            }
        }

        return false;
    }

    public void openClaimsMenu(Player player) {
        String townName = townManager.getEffectiveTown(player);
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous devez être dans une ville pour accéder à ce menu.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        // Admin override = accès maire/architecte
        boolean isAdminOverride = townManager.isAdminOverride(player, townName);
        TownRole role = isAdminOverride ? TownRole.MAIRE : town.getMemberRole(player.getUniqueId());

        Inventory inv = Bukkit.createInventory(null, 27, CLAIMS_TITLE);

        Chunk currentChunk = player.getLocation().getChunk();
        double claimCost = plugin.getConfig().getDouble("town.claim-cost-per-chunk", 500.0);

        // Claim le chunk actuel (slot 10)
        ItemStack claimItem = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta claimMeta = claimItem.getItemMeta();
        claimMeta.setDisplayName(ChatColor.GREEN + "Claim ce Chunk");
        List<String> claimLore = new ArrayList<>();
        claimLore.add(ChatColor.GRAY + "Coût: " + ChatColor.GOLD + String.format("%.2f€", claimCost));
        claimLore.add(ChatColor.GRAY + "Solde ville: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()));
        claimLore.add("");

        if (claimManager.isClaimed(currentChunk)) {
            String owner = claimManager.getClaimOwner(currentChunk);
            if (owner.equals(townName)) {
                claimLore.add(ChatColor.YELLOW + "Ce chunk est déjà claimé par votre ville");
            } else {
                claimLore.add(ChatColor.RED + "Ce chunk appartient à: " + owner);
            }
        } else {
            claimLore.add(ChatColor.YELLOW + "Cliquez pour claim ce chunk");
        }

        claimMeta.setLore(claimLore);
        claimItem.setItemMeta(claimMeta);
        inv.setItem(10, claimItem);

        // Informations du chunk actuel (slot 12)
        ItemStack infoItem = new ItemStack(Material.MAP);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.AQUA + "Informations du Chunk");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Position: " + ChatColor.WHITE +
            currentChunk.getX() + ", " + currentChunk.getZ());
        infoLore.add(ChatColor.GRAY + "Monde: " + ChatColor.WHITE + currentChunk.getWorld().getName());
        infoLore.add("");

        if (claimManager.isClaimed(currentChunk)) {
            String owner = claimManager.getClaimOwner(currentChunk);
            Plot plot = claimManager.getPlotAt(currentChunk);
            infoLore.add(ChatColor.GRAY + "Ville: " + ChatColor.GOLD + owner);
            if (plot != null) {
                infoLore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + plot.getType().getDisplayName());
                if (plot.getOwnerName() != null) {
                    infoLore.add(ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + plot.getOwnerName());
                }
                if (plot.isGrouped()) {
                    infoLore.add(ChatColor.LIGHT_PURPLE + "Groupe: " + ChatColor.WHITE + plot.getGroupName());
                    infoLore.add(ChatColor.GRAY + "Chunks: " + ChatColor.WHITE + plot.getChunks().size());
                }
            }
        } else {
            infoLore.add(ChatColor.GREEN + "Chunk libre - non claimé");
        }

        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(12, infoItem);

        // Unclaim le chunk actuel (slot 14)
        ItemStack unclaimItem = new ItemStack(Material.BARRIER);
        ItemMeta unclaimMeta = unclaimItem.getItemMeta();
        unclaimMeta.setDisplayName(ChatColor.RED + "Unclaim ce Chunk");
        List<String> unclaimLore = new ArrayList<>();
        double refundPercent = plugin.getConfig().getDouble("town.unclaim-refund-percentage", 75.0);
        double refund = claimCost * (refundPercent / 100.0);
        unclaimLore.add(ChatColor.GRAY + "Remboursement: " + ChatColor.GOLD + String.format("%.2f€", refund));
        unclaimLore.add("");

        if (!claimManager.isClaimed(currentChunk)) {
            unclaimLore.add(ChatColor.RED + "Ce chunk n'est pas claimé");
        } else {
            String owner = claimManager.getClaimOwner(currentChunk);
            if (owner.equals(townName)) {
                unclaimLore.add(ChatColor.YELLOW + "Cliquez pour unclaim ce chunk");
            } else {
                unclaimLore.add(ChatColor.RED + "Ce chunk n'appartient pas à votre ville");
            }
        }

        unclaimMeta.setLore(unclaimLore);
        unclaimItem.setItemMeta(unclaimMeta);
        inv.setItem(14, unclaimItem);

        // Gérer ce terrain (slot 19 - si le chunk actuel est un terrain de la ville)
        Plot currentPlot = claimManager.getPlotAt(currentChunk);
        if (currentPlot != null && claimManager.isClaimed(currentChunk) && claimManager.getClaimOwner(currentChunk).equals(townName)) {
            ItemStack managePlotItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta managePlotMeta = managePlotItem.getItemMeta();
            managePlotMeta.setDisplayName(ChatColor.BLUE + "Gérer ce Terrain");
            List<String> managePlotLore = new ArrayList<>();

            int totalChunks = currentPlot.getChunks().size();
            int surface = totalChunks * 256;

            if (currentPlot.isGrouped()) {
                managePlotLore.add(ChatColor.LIGHT_PURPLE + "Groupe: " + ChatColor.WHITE + currentPlot.getGroupName());
            }
            managePlotLore.add(ChatColor.GRAY + "Surface: " + ChatColor.WHITE + surface + "m² (" + totalChunks + " chunks)");
            managePlotLore.add(ChatColor.GRAY + "─────────────────");
            managePlotLore.add(ChatColor.GRAY + "Vendre, louer ou");
            managePlotLore.add(ChatColor.GRAY + "modifier ce terrain");
            managePlotLore.add("");
            managePlotLore.add(ChatColor.YELLOW + "Cliquez pour gérer");
            managePlotMeta.setLore(managePlotLore);

            managePlotItem.setItemMeta(managePlotMeta);
            inv.setItem(19, managePlotItem);
        }

        // Statistiques de claims (slot 21)
        ItemStack statsItem = new ItemStack(Material.BOOK);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(ChatColor.GOLD + "Statistiques de Claims");
        List<String> statsLore = new ArrayList<>();
        int totalClaims = claimManager.getClaimCount(townName);
        statsLore.add(ChatColor.GRAY + "Total chunks claimés: " + ChatColor.WHITE + totalClaims);
        statsLore.add(ChatColor.GRAY + "Banque ville: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()));
        statsLore.add(ChatColor.GRAY + "Coût par claim: " + ChatColor.GOLD + String.format("%.2f€", claimCost));
        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        inv.setItem(21, statsItem);

        // Gérer les regroupements (slot 23 - Maire/Adjoint uniquement)
        if (role == TownRole.MAIRE || role == TownRole.ADJOINT) {
            ItemStack groupItem = new ItemStack(Material.CHEST_MINECART);
            ItemMeta groupMeta = groupItem.getItemMeta();
            groupMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Gérer les Regroupements de Terrains");
            List<String> groupLore = new ArrayList<>();
            groupLore.add(ChatColor.GRAY + "Assembler plusieurs parcelles");
            groupLore.add(ChatColor.GRAY + "en un seul grand terrain");
            groupLore.add("");
            groupLore.add(ChatColor.YELLOW + "• Minimum 2 parcelles privées");
            groupLore.add(ChatColor.YELLOW + "• Même propriétaire requis");
            groupLore.add("");
            groupLore.add(ChatColor.AQUA + "Cliquez pour gérer les groupes");
            groupMeta.setLore(groupLore);
            groupItem.setItemMeta(groupMeta);
            inv.setItem(23, groupItem);
        }

        // Retour vers Administration (haut gauche)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour");
        List<String> backLore = new ArrayList<>();
        backLore.add(ChatColor.GRAY + "Retour au menu Administration");
        backMeta.setLore(backLore);
        backItem.setItemMeta(backMeta);
        inv.setItem(0, backItem);

        // Fermer (haut droite)
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "✖ Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(8, closeItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // Gérer le menu de sélection de type de terrain
        if (title.equals(TYPE_SELECTION_TITLE)) {
            handleTypeSelectionClick(event);
            return;
        }

        // Gérer le menu de sélection de sous-type municipal
        if (title.equals(SUBTYPE_SELECTION_TITLE)) {
            handleSubtypeSelectionClick(event);
            return;
        }

        if (!title.equals(CLAIMS_TITLE)) {
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

        // NPE Guard: Vérifier que l'item a une metadata et un displayName
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
            return;
        }

        String townName = townManager.getEffectiveTown(player);
        if (townName == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Vous n'êtes plus dans une ville.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        // Admin override = accès maire/architecte
        boolean isAdminOverride = townManager.isAdminOverride(player, townName);
        TownRole role = isAdminOverride ? TownRole.MAIRE : town.getMemberRole(player.getUniqueId());
        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        Chunk currentChunk = player.getLocation().getChunk();

        // === ACTIONS RÉSERVÉES AUX ADMINS ===
        if (displayName.contains("Claim ce Chunk") || displayName.contains("Unclaim ce Chunk")) {
            // Vérifier permissions admin
            if (role == null || (!role.canManageClaims() && role != TownRole.MAIRE)) {
                player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de gérer les claims.");
                player.closeInventory();
                return;
            }

            if (displayName.contains("Claim ce Chunk")) {
                handleClaim(player, townName, town, currentChunk);
            } else {
                handleUnclaim(player, townName, currentChunk);
            }
        }
        // === GÉRER TERRAIN ===
        else if (displayName.contains("Gérer ce Terrain")) {
            Plot plot = claimManager.getPlotAt(currentChunk);
            boolean isAdmin = (role != null && (role.canManageClaims() || role == TownRole.MAIRE));
            boolean isOwnerOrRenter = (plot != null &&
                (plot.isOwnedBy(player.getUniqueId()) || plot.isRentedBy(player.getUniqueId())));

            if (!isAdmin && !isOwnerOrRenter) {
                player.sendMessage(ChatColor.RED + "Vous devez être propriétaire/locataire de ce terrain ou administrateur.");
                player.closeInventory();
                return;
            }

            player.closeInventory();

            if (plot != null && plotManagementGUI != null) {
                plotManagementGUI.openPlotMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Le système de gestion de parcelles n'est pas disponible.");
            }
        }
        // === GÉRER REGROUPEMENTS : Admin OU possède des terrains ===
        else if (displayName.contains("Gérer les Regroupements de Terrains")) {
            boolean isAdmin = (role != null && (role.canManageClaims() || role == TownRole.MAIRE));
            boolean ownsPlots = hasOwnedPlots(player, town);

            if (!isAdmin && !ownsPlots) {
                player.sendMessage(ChatColor.RED + "Vous devez posséder des terrains ou être administrateur.");
                player.closeInventory();
                return;
            }

            player.closeInventory();
            plugin.getPlotGroupManagementGUI().openMainMenu(player, townName);
        }
        // === RETOUR ===
        else if (displayName.contains("Retour")) {
            player.closeInventory();
            // Retour vers le menu Administration
            if (adminGUI != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    adminGUI.openAdminMenu(player, town);
                }, 1L);
            }
        }
        // === FERMER ===
        else if (displayName.contains("Fermer")) {
            player.closeInventory();
        }
    }

    private void handleClaim(Player player, String townName, Town town, Chunk chunk) {
        // Vérifier si déjà claimé
        if (claimManager.isClaimed(chunk)) {
            String owner = claimManager.getClaimOwner(chunk);
            if (owner.equals(townName)) {
                player.sendMessage(ChatColor.YELLOW + "Ce chunk est déjà claimé par votre ville.");
            } else {
                player.sendMessage(ChatColor.RED + "Ce chunk appartient déjà à " + owner + ".");
            }
            player.closeInventory();
            return;
        }

        // Vérifier et effectuer le claim
        double claimCost = plugin.getConfig().getDouble("town.claim-cost-per-chunk", 500.0);

        // Vérifier le solde AVANT de tenter le claim pour un message d'erreur approprié
        if (town.getBankBalance() < claimCost) {
            player.sendMessage(ChatColor.RED + "Solde insuffisant dans la banque de la ville !");
            player.sendMessage(ChatColor.GRAY + "Requis: " + ChatColor.GOLD + String.format("%.2f€", claimCost) +
                ChatColor.GRAY + " | Disponible: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()));
            player.closeInventory();
            return;
        }

        // Vérifier l'adjacence AVANT de tenter le claim
        if (!claimManager.isAdjacentToTownClaim(townName, chunk)) {
            player.sendMessage(ChatColor.RED + "✗ Les claims doivent être adjacents (côte à côte) !");
            player.sendMessage(ChatColor.YELLOW + "→ Vous ne pouvez pas claim des chunks espacés.");
            player.sendMessage(ChatColor.GRAY + "Le chunk doit toucher au moins un chunk existant de votre ville.");
            player.closeInventory();
            return;
        }

        if (claimManager.claimChunk(townName, chunk, claimCost)) {
            player.sendMessage(ChatColor.GREEN + "Chunk claimé avec succès pour " + claimCost + "€ !");
            player.sendMessage(ChatColor.GRAY + "Position: " + chunk.getX() + ", " + chunk.getZ());

            // Récupérer le plot nouvellement créé et proposer le choix du type
            Plot newPlot = claimManager.getPlotAt(chunk);
            if (newPlot != null) {
                player.closeInventory();
                // Stocker le plot pour la sélection de type
                pendingTypePlots.put(player.getUniqueId(), newPlot);
                // Ouvrir directement le menu de sélection de type
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openTypeSelectionMenu(player, newPlot, townName);
                }, 2L);
            } else {
                player.closeInventory();
            }
        } else {
            player.sendMessage(ChatColor.RED + "Impossible de claim ce chunk.");
            player.closeInventory();
        }
    }

    /**
     * Ouvre le menu de sélection du type de terrain après un claim
     */
    private void openTypeSelectionMenu(Player player, Plot plot, String townName) {
        Inventory inv = Bukkit.createInventory(null, 27, TYPE_SELECTION_TITLE);

        // Titre informatif
        ItemStack infoItem = new ItemStack(Material.MAP);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Nouveau Terrain Claimé");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Position: " + ChatColor.WHITE + plot.getCoordinates());
        infoLore.add("");
        infoLore.add(ChatColor.YELLOW + "Choisissez le type de terrain");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Afficher les 4 types de terrain
        int slot = 10;
        for (PlotType type : PlotType.values()) {
            ItemStack typeItem = new ItemStack(type.getIcon());
            ItemMeta typeMeta = typeItem.getItemMeta();
            typeMeta.setDisplayName(ChatColor.YELLOW + type.getDisplayName());

            List<String> typeLore = new ArrayList<>();
            typeLore.add(ChatColor.GRAY + "Taxe: " + ChatColor.GOLD + type.getDailyTax() + "€/jour");
            typeLore.add("");

            // Description selon le type
            switch (type) {
                case MUNICIPAL:
                    typeLore.add(ChatColor.AQUA + "Bâtiments publics");
                    typeLore.add(ChatColor.GRAY + "(Mairie, Commissariat, etc.)");
                    break;
                case PUBLIC:
                    typeLore.add(ChatColor.GREEN + "Espace ouvert à tous");
                    typeLore.add(ChatColor.GRAY + "(Parcs, routes, etc.)");
                    break;
                case PARTICULIER:
                    typeLore.add(ChatColor.LIGHT_PURPLE + "Terrain résidentiel");
                    typeLore.add(ChatColor.GRAY + "(Peut être vendu/loué)");
                    break;
                case PROFESSIONNEL:
                    typeLore.add(ChatColor.GOLD + "Terrain commercial");
                    typeLore.add(ChatColor.GRAY + "(Pour les entreprises)");
                    break;
            }

            typeLore.add("");
            typeLore.add(ChatColor.YELLOW + "▶ Cliquez pour sélectionner");

            typeMeta.setLore(typeLore);
            typeItem.setItemMeta(typeMeta);
            inv.setItem(slot++, typeItem);
        }

        // Bouton Annuler (garde le type par défaut PUBLIC)
        ItemStack cancelItem = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancelItem.getItemMeta();
        cancelMeta.setDisplayName(ChatColor.RED + "Garder par défaut (Public)");
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add(ChatColor.GRAY + "Le terrain restera de type PUBLIC");
        cancelMeta.setLore(cancelLore);
        cancelItem.setItemMeta(cancelMeta);
        inv.setItem(22, cancelItem);

        player.openInventory(inv);
    }

    /**
     * Ouvre le menu de sélection du sous-type municipal
     */
    private void openMunicipalSubtypeMenu(Player player, Plot plot, String townName) {
        Inventory inv = Bukkit.createInventory(null, 27, SUBTYPE_SELECTION_TITLE);

        // Titre informatif
        ItemStack infoItem = new ItemStack(Material.MAP);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.setDisplayName(ChatColor.GOLD + "Terrain Municipal");
        List<String> infoLore = new ArrayList<>();
        infoLore.add(ChatColor.GRAY + "Position: " + ChatColor.WHITE + plot.getCoordinates());
        infoLore.add("");
        infoLore.add(ChatColor.YELLOW + "Choisissez la fonction du bâtiment");
        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(4, infoItem);

        // Afficher tous les sous-types municipaux
        int slot = 10;
        for (MunicipalSubType subtype : MunicipalSubType.values()) {
            ItemStack subtypeItem = new ItemStack(subtype.getIcon());
            ItemMeta subtypeMeta = subtypeItem.getItemMeta();
            subtypeMeta.setDisplayName(ChatColor.LIGHT_PURPLE + subtype.getDisplayName());

            List<String> subtypeLore = new ArrayList<>();
            subtypeLore.add("");

            // Description selon le sous-type
            switch (subtype) {
                case NONE:
                    subtypeLore.add(ChatColor.GRAY + "Bâtiment sans fonction");
                    subtypeLore.add(ChatColor.GRAY + "particulière");
                    break;
                case MAIRIE:
                    subtypeLore.add(ChatColor.GRAY + "Bâtiment administratif");
                    subtypeLore.add(ChatColor.GRAY + "Accès: Maire + Adjoint");
                    break;
                case COMMISSARIAT:
                    subtypeLore.add(ChatColor.GRAY + "Poste de police");
                    subtypeLore.add(ChatColor.GRAY + "Accès: Policiers");
                    break;
                case TRIBUNAL:
                    subtypeLore.add(ChatColor.GRAY + "Palais de justice");
                    subtypeLore.add(ChatColor.GRAY + "Accès: Juges");
                    subtypeLore.add(ChatColor.YELLOW + "Requis pour juger!");
                    break;
                case LA_POSTE:
                    subtypeLore.add(ChatColor.GRAY + "Bureau de poste");
                    subtypeLore.add(ChatColor.GRAY + "Accès: Tous les citoyens");
                    break;
                case BANQUE:
                    subtypeLore.add(ChatColor.GRAY + "Établissement bancaire");
                    subtypeLore.add(ChatColor.GRAY + "Accès: Tous les citoyens");
                    break;
                case HOPITAL:
                    subtypeLore.add(ChatColor.GRAY + "Centre médical");
                    subtypeLore.add(ChatColor.GRAY + "Accès: Médecins");
                    break;
            }

            subtypeLore.add("");
            subtypeLore.add(ChatColor.YELLOW + "▶ Cliquez pour sélectionner");

            subtypeMeta.setLore(subtypeLore);
            subtypeItem.setItemMeta(subtypeMeta);
            inv.setItem(slot++, subtypeItem);
        }

        // Bouton Retour (garde NONE)
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "← Retour (garder sans fonction)");
        backItem.setItemMeta(backMeta);
        inv.setItem(22, backItem);

        player.openInventory(inv);
    }

    /**
     * Gère les clics dans le menu de sélection de type de terrain
     */
    private void handleTypeSelectionClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        Plot plot = pendingTypePlots.get(player.getUniqueId());

        if (plot == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Erreur: Contexte perdu. Veuillez recommencer.");
            return;
        }

        String townName = plot.getTownName();

        // Bouton Annuler - garder PUBLIC
        if (displayName.contains("Garder par défaut")) {
            pendingTypePlots.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "✓ Terrain configuré en " + ChatColor.AQUA + "PUBLIC");
            townManager.saveTownsNow();
            // Retour au menu des claims
            Bukkit.getScheduler().runTaskLater(plugin, () -> openClaimsMenu(player), 2L);
            return;
        }

        // Chercher le type sélectionné
        PlotType selectedType = null;
        for (PlotType type : PlotType.values()) {
            if (displayName.contains(type.getDisplayName())) {
                selectedType = type;
                break;
            }
        }

        if (selectedType != null) {
            plot.setType(selectedType);

            // Si type MUNICIPAL, ouvrir directement le menu de sous-type
            if (selectedType == PlotType.MUNICIPAL) {
                player.closeInventory();
                player.sendMessage(ChatColor.GREEN + "✓ Type défini: " + ChatColor.AQUA + selectedType.getDisplayName());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    openMunicipalSubtypeMenu(player, plot, townName);
                }, 2L);
            } else {
                // Pour les autres types, terminer et sauvegarder
                pendingTypePlots.remove(player.getUniqueId());
                player.closeInventory();

                // Générer un numéro de terrain si nécessaire
                if (selectedType == PlotType.PARTICULIER || selectedType == PlotType.PROFESSIONNEL) {
                    Town town = townManager.getTown(townName);
                    if (town != null) {
                        String plotNumber = town.generateUniquePlotNumber();
                        if (plotNumber != null) {
                            plot.setPlotNumber(plotNumber);
                            player.sendMessage(ChatColor.GREEN + "✓ Type défini: " + ChatColor.AQUA + selectedType.getDisplayName());
                            player.sendMessage(ChatColor.GOLD + "→ Numéro de terrain: " + ChatColor.BOLD + plotNumber);
                        }
                    }
                } else {
                    player.sendMessage(ChatColor.GREEN + "✓ Type défini: " + ChatColor.AQUA + selectedType.getDisplayName());
                }

                townManager.saveTownsNow();
                // Retour au menu des claims
                Bukkit.getScheduler().runTaskLater(plugin, () -> openClaimsMenu(player), 2L);
            }
        }
    }

    /**
     * Gère les clics dans le menu de sélection de sous-type municipal
     */
    private void handleSubtypeSelectionClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) {
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        Plot plot = pendingTypePlots.get(player.getUniqueId());

        if (plot == null) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "Erreur: Contexte perdu. Veuillez recommencer.");
            return;
        }

        // Bouton Retour - garder NONE
        if (displayName.contains("Retour")) {
            pendingTypePlots.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "✓ Terrain municipal configuré (sans fonction spécifique)");
            townManager.saveTownsNow();
            // Retour au menu des claims
            Bukkit.getScheduler().runTaskLater(plugin, () -> openClaimsMenu(player), 2L);
            return;
        }

        // Chercher le sous-type sélectionné
        MunicipalSubType selectedSubtype = null;
        for (MunicipalSubType subtype : MunicipalSubType.values()) {
            if (displayName.contains(subtype.getDisplayName())) {
                selectedSubtype = subtype;
                break;
            }
        }

        if (selectedSubtype != null) {
            plot.setMunicipalSubType(selectedSubtype);
            pendingTypePlots.remove(player.getUniqueId());
            player.closeInventory();

            player.sendMessage(ChatColor.GREEN + "✓ Sous-type défini: " + ChatColor.LIGHT_PURPLE + selectedSubtype.getDisplayName());
            townManager.saveTownsNow();

            // Retour au menu des claims
            Bukkit.getScheduler().runTaskLater(plugin, () -> openClaimsMenu(player), 2L);
        }
    }

    private void handleUnclaim(Player player, String townName, Chunk chunk) {
        // Vérifier si ce chunk appartient à la ville
        if (!claimManager.isClaimed(chunk)) {
            player.sendMessage(ChatColor.RED + "Ce chunk n'est pas claimé.");
            player.closeInventory();
            return;
        }

        String owner = claimManager.getClaimOwner(chunk);
        if (!owner.equals(townName)) {
            player.sendMessage(ChatColor.RED + "Ce chunk n'appartient pas à votre ville.");
            player.closeInventory();
            return;
        }

        // Effectuer l'unclaim
        double refundPercent = plugin.getConfig().getDouble("town.unclaim-refund-percentage", 75.0);
        double refund = claimManager.unclaimChunk(townName, chunk, refundPercent);

        if (refund > 0) {
            player.sendMessage(ChatColor.GREEN + "Chunk unclaimed avec succès !");
            player.sendMessage(ChatColor.GOLD + "Remboursement: " + String.format("%.2f€", refund));
            player.closeInventory();
        } else {
            player.sendMessage(ChatColor.RED + "Erreur lors de l'unclaim du chunk.");
            player.closeInventory();
        }
    }
}
