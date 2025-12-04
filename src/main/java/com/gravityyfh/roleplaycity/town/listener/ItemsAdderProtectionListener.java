package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.heist.data.Heist;
import com.gravityyfh.roleplaycity.heist.data.HeistPhase;
import com.gravityyfh.roleplaycity.heist.manager.HeistManager;
import com.gravityyfh.roleplaycity.town.data.*;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import dev.lone.itemsadder.api.CustomBlock;
import dev.lone.itemsadder.api.CustomFurniture;
import dev.lone.itemsadder.api.Events.CustomBlockBreakEvent;
import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Listener de protection pour les items custom ItemsAdder dans les villes.
 * Empêche la destruction de meubles (Furniture) et blocs custom dans les zones protégées.
 *
 * Ce listener ne doit être enregistré que si ItemsAdder est présent sur le serveur.
 */
public class ItemsAdderProtectionListener implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    public ItemsAdderProtectionListener(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
    }

    /**
     * Protège les meubles (Furniture) ItemsAdder dans les villes
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnitureBreak(FurnitureBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        CustomFurniture furniture = event.getFurniture();
        if (furniture == null) return;

        Location location = furniture.getArmorstand().getLocation();

        // BYPASS: Bombe de cambriolage peut toujours être cassée (gérée par HeistBombListener)
        String namespacedId = furniture.getNamespacedID();
        if (namespacedId != null && (namespacedId.contains("bomb") || namespacedId.contains("bombe"))) {
            return; // Laisser HeistBombListener gérer
        }

        // Vérifier les permissions de construction
        if (!canBreak(player, location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas casser ce meuble ici.");
            notifyPlotInfo(player, location);
        }
    }

    /**
     * Protège les blocs custom ItemsAdder dans les villes
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCustomBlockBreak(CustomBlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        Location location = event.getBlock().getLocation();

        // Vérifier les permissions de construction
        if (!canBreak(player, location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas casser ce bloc custom ici.");
            notifyPlotInfo(player, location);
        }
    }

    /**
     * Vérifie si un joueur peut casser à cet emplacement
     */
    private boolean canBreak(Player player, Location location) {
        // Admins bypass
        if (player.hasPermission("roleplaycity.town.admin")) {
            return true;
        }

        // HEIST BYPASS: Pendant la phase de vol, tout le monde peut casser
        if (canBypassForHeist(player, location)) {
            return true;
        }

        // Récupérer la ville
        String townName = claimManager.getClaimOwner(location);
        if (townName == null) {
            // Chunk non claimé, autorisé
            return true;
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return true; // Sécurité si données corrompues
        }

        Plot plot = claimManager.getPlotAt(location);
        if (plot == null) {
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        // Utiliser la méthode canBuild du Plot (gère individuels et groupés)
        return plot.canBuild(playerUuid, town);
    }

    /**
     * Vérifie si un joueur peut bypass les protections à cause d'un cambriolage en cours.
     */
    private boolean canBypassForHeist(Player player, Location location) {
        HeistManager heistManager = plugin.getHeistManager();
        if (heistManager == null) {
            return false;
        }

        Heist heist = heistManager.getActiveHeistAt(location);
        if (heist == null) {
            return false;
        }

        // Pendant la phase de vol: TOUT LE MONDE a accès libre au terrain
        return heist.getPhase() == HeistPhase.ROBBERY;
    }

    /**
     * Notifie le joueur des informations sur la parcelle
     */
    private void notifyPlotInfo(Player player, Location location) {
        Plot plot = claimManager.getPlotAt(location);
        if (plot == null) {
            return;
        }

        String townName = claimManager.getClaimOwner(location);
        player.sendMessage(ChatColor.GRAY + "Cette parcelle appartient à: " + ChatColor.GOLD + townName);
        player.sendMessage(ChatColor.GRAY + "Type: " + ChatColor.AQUA + plot.getType().getDisplayName());

        if (plot.getOwnerName() != null) {
            player.sendMessage(ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + plot.getOwnerName());
        }
    }
}
