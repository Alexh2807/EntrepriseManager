package com.gravityyfh.roleplaycity.town.gui;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
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

        // Gérer cette parcelle (si claimée)
        if (claimManager.isClaimed(currentChunk) && claimManager.getClaimOwner(currentChunk).equals(townName)) {
            ItemStack managePlotItem = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta managePlotMeta = managePlotItem.getItemMeta();
            managePlotMeta.setDisplayName(ChatColor.BLUE + "Gérer cette Parcelle");
            List<String> managePlotLore = new ArrayList<>();
            managePlotLore.add(ChatColor.GRAY + "Vendre, louer ou");
            managePlotLore.add(ChatColor.GRAY + "modifier cette parcelle");
            managePlotLore.add("");
            managePlotLore.add(ChatColor.YELLOW + "Cliquez pour gérer");
            managePlotMeta.setLore(managePlotLore);
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

        // Vérifier les permissions
        TownRole role = town.getMemberRole(player.getUniqueId());
        if (role == null || (!role.canManageClaims() && role != TownRole.MAIRE)) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de gérer les claims.");
            return;
        }

        String displayName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        Chunk currentChunk = player.getLocation().getChunk();

        if (displayName.contains("Claim ce Chunk")) {
            handleClaim(player, townName, town, currentChunk);
        } else if (displayName.contains("Unclaim ce Chunk")) {
            handleUnclaim(player, townName, town, currentChunk);
        } else if (displayName.contains("Gérer cette Parcelle")) {
            player.closeInventory();
            if (plotManagementGUI != null) {
                plotManagementGUI.openPlotMenu(player);
            } else {
                player.sendMessage(ChatColor.RED + "Le système de gestion de parcelles n'est pas disponible.");
            }
        } else if (displayName.contains("Fermer")) {
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

        if (claimManager.claimChunk(townName, chunk, claimCost)) {
            player.sendMessage(ChatColor.GREEN + "Chunk claimé avec succès pour " + claimCost + "€ !");
            player.sendMessage(ChatColor.GRAY + "Position: " + chunk.getX() + ", " + chunk.getZ());
            player.closeInventory();
        } else {
            player.sendMessage(ChatColor.RED + "Impossible de claim ce chunk. Vérifiez le solde de la ville.");
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
