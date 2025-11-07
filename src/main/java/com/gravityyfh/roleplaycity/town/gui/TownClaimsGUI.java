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
     * ⚠️ NOUVEAU SYSTÈME : Vérifie plots individuels ET PlotGroups
     */
    private boolean hasOwnedPlots(Player player, Town town) {
        UUID playerUuid = player.getUniqueId();

        // Vérifier plots individuels
        for (Plot plot : town.getPlots().values()) {
            if (plot.isOwnedBy(playerUuid)) {
                return true;
            }
        }

        // ⚠️ NOUVEAU : Vérifier PlotGroups
        for (com.gravityyfh.roleplaycity.town.data.PlotGroup group : town.getPlotGroups().values()) {
            if (playerUuid.equals(group.getOwnerUuid())) {
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

        Inventory inv = Bukkit.createInventory(null, 27, CLAIMS_TITLE);

        // Claim le chunk actuel
        ItemStack claimItem = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta claimMeta = claimItem.getItemMeta();
        claimMeta.setDisplayName(ChatColor.GREEN + "Claim ce Chunk");
        List<String> claimLore = new ArrayList<>();
        double claimCost = plugin.getConfig().getDouble("town.claim-cost-per-chunk", 500.0);
        claimLore.add(ChatColor.GRAY + "Coût: " + ChatColor.GOLD + String.format("%.2f€", claimCost));
        claimLore.add(ChatColor.GRAY + "Solde ville: " + ChatColor.GOLD + String.format("%.2f€", town.getBankBalance()));
        claimLore.add("");

        Chunk currentChunk = player.getLocation().getChunk();
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
        inv.setItem(11, claimItem);

        // Unclaim le chunk actuel
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
        inv.setItem(13, unclaimItem);

        // Informations sur le chunk actuel
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
            // Fallback: si aucun Plot, tenter un PlotGroup autonome directement
            if (plot == null) {
                com.gravityyfh.roleplaycity.town.data.PlotGroup groupAt =
                    town.getPlotGroupAt(currentChunk.getWorld().getName(), currentChunk.getX(), currentChunk.getZ());
                if (groupAt != null) {
                    boolean isAdminOrOwner = (role != null && (role.canManageClaims() || role == TownRole.MAIRE))
                        || groupAt.isOwnedBy(player.getUniqueId()) || groupAt.isRentedBy(player.getUniqueId());
                    if (!isAdminOrOwner) {
                        player.sendMessage(ChatColor.RED + "Vous devez Ǧtre propriǸtaire/locataire de ce groupe ou administrateur.");
                        player.closeInventory();
                        return;
                    }
                    player.closeInventory();
                    plugin.getPlotGroupDetailGUI().openGroupDetailMenu(player, townName, groupAt.getGroupId());
                    return;
                }
            }
            infoLore.add(ChatColor.GRAY + "Ville: " + ChatColor.GOLD + owner);
            if (plot != null) {
                infoLore.add(ChatColor.GRAY + "Type: " + ChatColor.AQUA + plot.getType().getDisplayName());
                if (plot.getOwnerName() != null) {
                    infoLore.add(ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + plot.getOwnerName());
                }
            }
        } else {
            infoLore.add(ChatColor.GREEN + "Chunk libre - non claimé");
        }

        infoMeta.setLore(infoLore);
        infoItem.setItemMeta(infoMeta);
        inv.setItem(15, infoItem);

        // SYSTÈME CONTEXTUEL : Détecter si on est sur un groupe ou une parcelle individuelle
        Plot currentPlot = claimManager.getPlotAt(currentChunk);
        com.gravityyfh.roleplaycity.town.data.PlotGroup currentGroup = null;
        boolean isOnGroup = false;

        if (currentPlot != null && claimManager.isClaimed(currentChunk) && claimManager.getClaimOwner(currentChunk).equals(townName)) {
            currentGroup = town.findPlotGroupByPlot(currentPlot);
            isOnGroup = (currentGroup != null);
        }

        // Option contextuelle : Gérer ce terrain (parcelle OU groupe)
        if (claimManager.isClaimed(currentChunk) && claimManager.getClaimOwner(currentChunk).equals(townName)) {
            ItemStack managePlotItem = new ItemStack(isOnGroup ? Material.ENDER_CHEST : Material.WRITABLE_BOOK);
            ItemMeta managePlotMeta = managePlotItem.getItemMeta();

            if (isOnGroup) {
                // On est sur un groupe
                int totalSurface = currentGroup.getChunkKeys().size() * 256;
                managePlotMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Gérer ce Groupe de Terrains");
                List<String> managePlotLore = new ArrayList<>();
                managePlotLore.add(ChatColor.GRAY + "─────────────────");
                managePlotLore.add(ChatColor.YELLOW + "Groupe: " + ChatColor.WHITE + currentGroup.getGroupName());
                managePlotLore.add(ChatColor.YELLOW + "Parcelles: " + ChatColor.WHITE + currentGroup.getChunkKeys().size());
                managePlotLore.add(ChatColor.YELLOW + "Surface totale: " + ChatColor.WHITE + totalSurface + "m²");
                managePlotLore.add(ChatColor.GRAY + "─────────────────");
                managePlotLore.add(ChatColor.GRAY + "Vendre, louer ou modifier");
                managePlotLore.add(ChatColor.GRAY + "ce groupe comme un seul terrain");
                managePlotLore.add("");
                managePlotLore.add(ChatColor.GREEN + "Cliquez pour gérer ce groupe");
                managePlotMeta.setLore(managePlotLore);
            } else {
                // Parcelle individuelle
                managePlotMeta.setDisplayName(ChatColor.BLUE + "Gérer cette Parcelle");
                List<String> managePlotLore = new ArrayList<>();
                managePlotLore.add(ChatColor.GRAY + "Surface: " + ChatColor.WHITE + "256m² (1 chunk)");
                managePlotLore.add(ChatColor.GRAY + "─────────────────");
                managePlotLore.add(ChatColor.GRAY + "Vendre, louer ou");
                managePlotLore.add(ChatColor.GRAY + "modifier cette parcelle");
                managePlotLore.add("");
                managePlotLore.add(ChatColor.YELLOW + "Cliquez pour gérer");
                managePlotMeta.setLore(managePlotLore);
            }

            managePlotItem.setItemMeta(managePlotMeta);
            inv.setItem(20, managePlotItem);
        }

        // Statistiques de la ville
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
        inv.setItem(22, statsItem);

        // Option contextuelle : Assembler/Désassembler selon le contexte
        TownRole role = town.getMemberRole(player.getUniqueId());
        if (role == TownRole.MAIRE || role == TownRole.ADJOINT) {
            if (isOnGroup) {
                // On est sur un groupe : proposer de DÉSASSEMBLER
                ItemStack ungroupItem = new ItemStack(Material.SHEARS);
                ItemMeta ungroupMeta = ungroupItem.getItemMeta();
                ungroupMeta.setDisplayName(ChatColor.RED + "Désassembler ce Groupe");
                List<String> ungroupLore = new ArrayList<>();
                ungroupLore.add(ChatColor.GRAY + "Séparer ce groupe en");
                ungroupLore.add(ChatColor.GRAY + "parcelles individuelles");
                ungroupLore.add("");
                ungroupLore.add(ChatColor.YELLOW + "Groupe actuel: " + ChatColor.WHITE + currentGroup.getChunkKeys().size() + " parcelles");
                ungroupLore.add("");
                ungroupLore.add(ChatColor.RED + "Cliquez pour désassembler");
                ungroupMeta.setLore(ungroupLore);
                ungroupItem.setItemMeta(ungroupMeta);
                inv.setItem(24, ungroupItem);
            } else if (currentPlot != null) {
                // Parcelle individuelle : proposer d'ASSEMBLER
                ItemStack groupItem = new ItemStack(Material.CHAIN);
                ItemMeta groupMeta = groupItem.getItemMeta();
                groupMeta.setDisplayName(ChatColor.AQUA + "Assembler avec d'autres Terrains");
                List<String> groupLore = new ArrayList<>();
                groupLore.add(ChatColor.GRAY + "Créer un grand terrain en");
                groupLore.add(ChatColor.GRAY + "assemblant des parcelles adjacentes");
                groupLore.add("");
                groupLore.add(ChatColor.YELLOW + "• Minimum 2 parcelles");
                groupLore.add(ChatColor.YELLOW + "• Doivent être adjacentes");
                groupLore.add(ChatColor.YELLOW + "• Même propriétaire");
                groupLore.add("");
                groupLore.add(ChatColor.GREEN + "Cliquez pour assembler");
                groupMeta.setLore(groupLore);
                groupItem.setItemMeta(groupMeta);
                inv.setItem(24, groupItem);
            }
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
        // === GÉRER TERRAIN : Contextuel (Parcelle OU Groupe) ===
        else if (displayName.contains("Gérer cette Parcelle") || displayName.contains("Gérer ce Groupe")) {
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

            if (plot != null) {
                com.gravityyfh.roleplaycity.town.data.PlotGroup group = town.findPlotGroupByPlot(plot);
                if (group != null) {
                    // Groupe : ouvrir le GUI de gestion de groupe
                    plugin.getPlotGroupDetailGUI().openGroupDetailMenu(player, townName, group.getGroupId());
                } else {
                    // Parcelle individuelle : ouvrir le GUI normal
                    if (plotManagementGUI != null) {
                        plotManagementGUI.openPlotMenu(player);
                    } else {
                        player.sendMessage(ChatColor.RED + "Le système de gestion de parcelles n'est pas disponible.");
                    }
                }
            }
        }
        // === ASSEMBLER : Créer un nouveau groupe ===
        else if (displayName.contains("Assembler avec d'autres Terrains")) {
            if (role == null || (role != TownRole.MAIRE && role != TownRole.ADJOINT)) {
                player.sendMessage(ChatColor.RED + "Seuls le Maire et l'Adjoint peuvent assembler des terrains.");
                player.closeInventory();
                return;
            }

            player.closeInventory();
            // Lancer le mode de groupement interactif
            plugin.getPlotGroupingListener().startGroupingSession(player, townName);
        }
        // === DÉSASSEMBLER : Supprimer un groupe ===
        else if (displayName.contains("Désassembler ce Groupe")) {
            if (role == null || (role != TownRole.MAIRE && role != TownRole.ADJOINT)) {
                player.sendMessage(ChatColor.RED + "Seuls le Maire et l'Adjoint peuvent désassembler des groupes.");
                player.closeInventory();
                return;
            }

            Plot plot = claimManager.getPlotAt(currentChunk);
            if (plot != null) {
                com.gravityyfh.roleplaycity.town.data.PlotGroup group = town.findPlotGroupByPlot(plot);
                if (group != null) {
                    // Vérifier si le groupe est loué
                    if (group.getRenterUuid() != null) {
                        player.sendMessage(ChatColor.RED + "Impossible de désassembler ce groupe : il est actuellement loué !");
                        player.closeInventory();
                        return;
                    }

                    // Désassembler directement le groupe
                    String groupName = group.getGroupName();
                    int plotCount = group.getChunkKeys().size();

                    // ⚠️ CORRECTION : Utiliser dissolveGroup() qui recrée les plots individuels
                    town.dissolveGroup(group.getGroupId());
                    townManager.saveTownsNow();

                    player.closeInventory();
                    player.sendMessage(ChatColor.GREEN + "Groupe '" + groupName + "' désassemblé avec succès !");
                    player.sendMessage(ChatColor.GRAY + "" + plotCount + " parcelles sont maintenant individuelles.");
                } else {
                    player.sendMessage(ChatColor.RED + "Aucun groupe détecté sur ce terrain.");
                    player.closeInventory();
                }
            }
        }
        // Gérer ce Groupe de Terrains (prise en charge explicite)
        else if (displayName.contains("G�rer ce Groupe de Terrains")) {
            boolean isAdmin = (role != null && (role.canManageClaims() || role == TownRole.MAIRE));
            com.gravityyfh.roleplaycity.town.data.PlotGroup groupAt =
                town.getPlotGroupAt(currentChunk.getWorld().getName(), currentChunk.getX(), currentChunk.getZ());
            boolean allowed = (groupAt != null) && (groupAt.isOwnedBy(player.getUniqueId()) || groupAt.isRentedBy(player.getUniqueId()));
            if (!isAdmin && !allowed) {
                player.sendMessage(ChatColor.RED + "Vous devez être propriétaire/locataire de ce groupe ou administrateur.");
                player.closeInventory();
                return;
            }
            if (groupAt != null) {
                player.closeInventory();
                plugin.getPlotGroupDetailGUI().openGroupDetailMenu(player, townName, groupAt.getGroupId());
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
