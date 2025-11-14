package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;

import java.util.UUID;
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

    private static final String CLAIMS_TITLE = ChatColor.DARK_GREEN + "🗺️ Gestion des Claims";

    public TownClaimsGUI(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
    }

    public void setPlotManagementGUI(TownPlotManagementGUI plotManagementGUI) {
        this.plotManagementGUI = plotManagementGUI;
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
        String townName = townManager.getPlayerTown(player.getUniqueId());
        if (townName == null) {
            player.sendMessage(ChatColor.RED + "Vous devez être dans une ville pour accéder à ce menu.");
            return;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        // Obtenir le rôle du joueur dans la ville
        TownRole role = town.getMemberRole(player.getUniqueId());

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

        // Fermer
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "Fermer");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
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

        String townName = townManager.getPlayerTown(player.getUniqueId());
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

        TownRole role = town.getMemberRole(player.getUniqueId());
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
                handleUnclaim(player, townName, town, currentChunk);
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
            player.closeInventory();
        } else {
            player.sendMessage(ChatColor.RED + "Impossible de claim ce chunk.");
            player.closeInventory();
        }
    }

    private void handleUnclaim(Player player, String townName, Town town, Chunk chunk) {
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
