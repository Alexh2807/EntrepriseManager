package com.gravityyfh.roleplaycity.police.listeners;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.ImprisonedPlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

import java.util.List;

/**
 * Listener pour gérer les restrictions des joueurs emprisonnés
 * Bloque la plupart des actions pendant la prison
 */
public class PrisonRestrictionListener implements Listener {

    private final RoleplayCity plugin;
    private final ImprisonedPlayerData imprisonedData;

    public PrisonRestrictionListener(RoleplayCity plugin, ImprisonedPlayerData imprisonedData) {
        this.plugin = plugin;
        this.imprisonedData = imprisonedData;
    }

    // ===== RESTRICTIONS POUR LES JOUEURS EMPRISONNÉS =====

    /**
     * Bloque l'interaction avec les entités
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (imprisonedData.isImprisoned(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✖ Vous ne pouvez pas faire cela en prison!");
        }
    }

    /**
     * Bloque l'accès à l'inventaire
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (imprisonedData.isImprisoned(player)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Bloque l'utilisation de commandes (sauf whitelist)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!imprisonedData.isImprisoned(player)) {
            return;
        }

        // Liste des commandes autorisées
        List<String> allowedCommands = plugin.getConfig()
            .getStringList("prison-system.allowed-commands");

        String command = event.getMessage().toLowerCase().split(" ")[0].substring(1);

        boolean allowed = false;
        for (String allowedCmd : allowedCommands) {
            if (command.equalsIgnoreCase(allowedCmd)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage("§c✖ Vous ne pouvez pas utiliser cette commande en prison!");
        }
    }

    /**
     * Bloque la casse de blocs
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerBreakBlock(BlockBreakEvent event) {
        if (imprisonedData.isImprisoned(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✖ Vous ne pouvez pas casser de blocs en prison!");
        }
    }

    /**
     * Bloque le placement de blocs
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerPlaceBlock(BlockPlaceEvent event) {
        if (imprisonedData.isImprisoned(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✖ Vous ne pouvez pas placer de blocs en prison!");
        }
    }

    /**
     * Bloque les attaques
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (imprisonedData.isImprisoned(player)) {
                event.setCancelled(true);
                player.sendMessage("§c✖ Vous ne pouvez pas attaquer en prison!");
            }
        }
    }

    /**
     * Bloque le drop d'items
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerDropItem(PlayerDropItemEvent event) {
        if (imprisonedData.isImprisoned(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✖ Vous ne pouvez pas jeter d'objets en prison!");
        }
    }

    /**
     * Bloque le changement de main
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerSwapHands(PlayerSwapHandItemsEvent event) {
        if (imprisonedData.isImprisoned(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Bloque les interactions (portes, boutons, coffres, ender pearl, etc.)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerInteract(PlayerInteractEvent event) {
        if (imprisonedData.isImprisoned(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c✖ Vous ne pouvez pas interagir avec des objets en prison!");
        }
    }

    /**
     * Bloque le ramassage d'items
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerPickupItem(PlayerPickupItemEvent event) {
        if (imprisonedData.isImprisoned(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Bloque la téléportation (commandes TP, ender pearl déjà bloqué via interact)
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        if (!imprisonedData.isImprisoned(player)) {
            return;
        }

        // Autoriser seulement les téléportations par le plugin (pour transfert prison)
        // Bloquer toutes les autres (commandes, ender pearl, etc.)
        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause != PlayerTeleportEvent.TeleportCause.PLUGIN &&
            cause != PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            event.setCancelled(true);
            player.sendMessage("§c✖ Vous ne pouvez pas vous téléporter en prison!");
        }
    }

    /**
     * Bloque le changement de monde
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onImprisonedPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (imprisonedData.isImprisoned(player)) {
            // Impossible normalement (téléportation déjà bloquée), mais par sécurité
            plugin.getLogger().warning("Joueur emprisonné a changé de monde: " + player.getName());
        }
    }

    /**
     * Restaure la boss bar lors de la reconnexion
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (imprisonedData.isImprisoned(player)) {
            imprisonedData.restoreBossBar(player);

            // Message de rappel
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c§l⛓️ VOUS ÊTES EN PRISON");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§7Temps restant: §c" +
                imprisonedData.getPrisonData(player.getUniqueId()).getFormattedRemainingTime());
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }
    }
}
