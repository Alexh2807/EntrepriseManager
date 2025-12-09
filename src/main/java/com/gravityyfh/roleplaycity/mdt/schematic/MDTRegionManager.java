package com.gravityyfh.roleplaycity.mdt.schematic;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.config.MDTConfig;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestionnaire de protection pour la région MDT
 * Protège la zone MDT pendant et en dehors des parties
 */
public class MDTRegionManager implements Listener {

    private final RoleplayCity plugin;
    private final MDTConfig config;
    private final MDTRushManager mdtManager;
    private final MDTSchematicManager schematicManager;

    // État de protection
    private boolean protectionEnabled = false;
    private String protectionReason = "";

    // Cache des permissions pour éviter les vérifications répétées
    private final Set<UUID> playersWithBypassPermission = ConcurrentHashMap.newKeySet();

    public MDTRegionManager(RoleplayCity plugin, MDTConfig config, MDTRushManager mdtManager, MDTSchematicManager schematicManager) {
        this.plugin = plugin;
        this.config = config;
        this.mdtManager = mdtManager;
        this.schematicManager = schematicManager;
    }

    /**
     * Active la protection de la région
     */
    public void enableProtection(String reason) {
        this.protectionEnabled = true;
        this.protectionReason = reason;
        plugin.getLogger().info("[MDT-Region] Protection activée: " + reason);
    }

    /**
     * Désactive la protection de la région
     */
    public void disableProtection() {
        this.protectionEnabled = false;
        this.protectionReason = "";
        plugin.getLogger().info("[MDT-Region] Protection désactivée");
    }

    /**
     * Vérifie si la protection est activée
     */
    public boolean isProtectionEnabled() {
        return protectionEnabled;
    }

    /**
     * Ajoute un joueur à la liste de bypass
     */
    public void addBypassPlayer(Player player) {
        if (player.hasPermission("mdt.admin") || player.hasPermission("mdt.bypass")) {
            playersWithBypassPermission.add(player.getUniqueId());
            player.sendMessage(ChatColor.GREEN + "✅ Vous pouvez maintenant modifier la région MDT");
        }
    }

    /**
     * Retire un joueur de la liste de bypass
     */
    public void removeBypassPlayer(Player player) {
        playersWithBypassPermission.remove(player.getUniqueId());
        player.sendMessage(ChatColor.YELLOW + "⚠️ Vous ne pouvez plus modifier la région MDT");
    }

    /**
     * Vérifie si un joueur peut bypasser la protection
     */
    private boolean canBypass(Player player) {
        // Vérifier si le joueur est dans le cache
        if (playersWithBypassPermission.contains(player.getUniqueId())) {
            return true;
        }

        // Vérifier la permission (et ajouter au cache si autorisé)
        if (player.hasPermission("mdt.admin") || player.hasPermission("mdt.bypass")) {
            playersWithBypassPermission.add(player.getUniqueId());
            return true;
        }

        return false;
    }

    /**
     * Vérifie si une position est dans la région MDT
     */
    private boolean isInMDTRegion(Location location) {
        if (location == null) return false;

        World world = config.getWorld();
        if (world == null || !location.getWorld().equals(world)) {
            return false;
        }

        Location min = config.getGameRegionMin();
        Location max = config.getGameRegionMax();

        if (min == null || max == null) return false;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= min.getBlockX() && x <= max.getBlockX() &&
               y >= min.getBlockY() && y <= max.getBlockY() &&
               z >= min.getBlockZ() && z <= max.getBlockZ();
    }

    /**
     * Vérifie si un bloc est dans la région MDT
     */
    private boolean isInMDTRegion(Block block) {
        return block != null && isInMDTRegion(block.getLocation());
    }

    /**
     * Message d'interdiction
     */
    private void sendProtectionMessage(Player player) {
        if (!player.hasPermission("mdt.silentbypass")) {
            player.sendMessage(ChatColor.RED + "⛔ " + ChatColor.BOLD + "Région MDT Protégée");
            player.sendMessage(ChatColor.GRAY + "Raison: " + ChatColor.YELLOW + protectionReason);
            player.sendMessage(ChatColor.GRAY + "Utilisez /mdt bypass pour obtenir la permission de modification");
        }
    }

    // ÉVÉNEMENTS DE PROTECTION

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!protectionEnabled || event.isCancelled()) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        if (isInMDTRegion(event.getBlock())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!protectionEnabled || event.isCancelled()) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        if (isInMDTRegion(event.getBlock())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!protectionEnabled || event.isCancelled()) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        if (isInMDTRegion(event.getBlockClicked())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        if (!protectionEnabled || event.isCancelled()) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        if (isInMDTRegion(event.getBlockClicked())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!protectionEnabled) return;

        // Retirer les blocs de la région MDT de l'explosion
        event.blockList().removeIf(this::isInMDTRegion);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!protectionEnabled) return;

        if (event.getRemover() instanceof Player) {
            Player player = (Player) event.getRemover();
            if (canBypass(player)) return;

            if (isInMDTRegion(event.getEntity().getLocation())) {
                event.setCancelled(true);
                sendProtectionMessage(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!protectionEnabled) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        if (isInMDTRegion(event.getEntity().getLocation())) {
            event.setCancelled(true);
            sendProtectionMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!protectionEnabled) return;

        Player player = event.getPlayer();
        if (canBypass(player)) return;

        // Protéger certains blocs interactifs dans la région
        if (event.hasBlock() && isProtectedBlock(event.getClickedBlock().getType())) {
            if (isInMDTRegion(event.getClickedBlock())) {
                event.setCancelled(true);
                sendProtectionMessage(player);
            }
        }
    }

    /**
     * Liste des blocs qui doivent être protégés même en cas d'interaction
     */
    private boolean isProtectedBlock(Material material) {
        switch (material) {
            case CHEST:
            case TRAPPED_CHEST:
            case SHULKER_BOX:
            case WHITE_SHULKER_BOX:
            case ORANGE_SHULKER_BOX:
            case MAGENTA_SHULKER_BOX:
            case LIGHT_BLUE_SHULKER_BOX:
            case YELLOW_SHULKER_BOX:
            case LIME_SHULKER_BOX:
            case PINK_SHULKER_BOX:
            case GRAY_SHULKER_BOX:
            case LIGHT_GRAY_SHULKER_BOX:
            case CYAN_SHULKER_BOX:
            case PURPLE_SHULKER_BOX:
            case BLUE_SHULKER_BOX:
            case BROWN_SHULKER_BOX:
            case GREEN_SHULKER_BOX:
            case RED_SHULKER_BOX:
            case BLACK_SHULKER_BOX:
            case HOPPER:
            case DROPPER:
            case DISPENSER:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case BREWING_STAND:
            case BEACON:
            case ENCHANTING_TABLE:
            case ANVIL:
            case CHIPPED_ANVIL:
            case DAMAGED_ANVIL:
            case CRAFTING_TABLE:
            case LOOM:
            case CARTOGRAPHY_TABLE:
            case SMITHING_TABLE:
            case FLETCHING_TABLE:
            case GRINDSTONE:
            case STONECUTTER:
                return true;
            default:
                return false;
        }
    }

    /**
     * Nettoie le cache des permissions quand un joueur quitte
     */
    public void onPlayerQuit(Player player) {
        playersWithBypassPermission.remove(player.getUniqueId());
    }
}