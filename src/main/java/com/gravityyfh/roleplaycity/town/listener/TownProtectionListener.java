package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.*;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.UUID;

/**
 * Listener de protection des territoires de ville
 * Empêche les actions non autorisées dans les chunks claimés
 */
public class TownProtectionListener implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    public TownProtectionListener(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location location = block.getLocation();

        if (!canBuild(player, location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas casser de blocs ici.");
            notifyPlotInfo(player, location);
            return;
        }

        // Vérifier si c'est un locataire essayant de casser un bloc protégé
        Plot plot = claimManager.getPlotAt(location);
        if (plot != null && plot.isRentedBy(player.getUniqueId())) {
            if (plot.isBlockProtected(location)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas casser les blocs existants de cette location.");
                player.sendMessage(ChatColor.GRAY + "Seul le propriétaire peut modifier ces blocs.");
                return;
            }
        }

        // Si c'est le propriétaire qui casse un bloc, le retirer de la protection
        if (plot != null && plot.isOwnedBy(player.getUniqueId()) && plot.getRenterUuid() != null) {
            plot.removeProtectedBlock(location);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location location = block.getLocation();

        if (!canBuild(player, location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas placer de blocs ici.");
            notifyPlotInfo(player, location);
            return;
        }

        // Si c'est le propriétaire qui place un bloc et qu'il y a un locataire, ajouter à la protection
        Plot plot = claimManager.getPlotAt(location);
        if (plot != null && plot.isOwnedBy(player.getUniqueId()) && plot.getRenterUuid() != null) {
            plot.addProtectedBlock(location);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        Player player = event.getPlayer();

        // Vérifier si c'est un bloc interactif (coffre, four, porte, bouton, etc.)
        if (isInteractiveBlock(block)) {
            if (!canInteract(player, block.getLocation())) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous ne pouvez pas interagir avec cela ici.");
                notifyPlotInfo(player, block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // Protection contre les dégâts aux entités dans les territoires
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        Location location = event.getEntity().getLocation();

        if (!canDamageEntity(player, location)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas attaquer des entités ici.");
        }
    }

    /**
     * Vérifie si un joueur peut construire à cet emplacement
     */
    private boolean canBuild(Player player, Location location) {
        // Admins bypass
        if (player.hasPermission("roleplaycity.town.admin")) {
            return true;
        }

        Plot plot = claimManager.getPlotAt(location);
        if (plot == null) {
            // Chunk non claimé, autorisé
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        String townName = claimManager.getClaimOwner(location);
        Town town = townManager.getTown(townName);

        if (town == null) {
            return true; // Sécurité si données corrompues
        }

        // Vérifier les permissions de construction via Plot
        return plot.canBuild(playerUuid, town);
    }

    /**
     * Vérifie si un joueur peut interagir à cet emplacement
     */
    private boolean canInteract(Player player, Location location) {
        // Admins bypass
        if (player.hasPermission("roleplaycity.town.admin")) {
            return true;
        }

        Plot plot = claimManager.getPlotAt(location);
        if (plot == null) {
            // Chunk non claimé, autorisé
            return true;
        }

        UUID playerUuid = player.getUniqueId();
        String townName = claimManager.getClaimOwner(location);
        Town town = townManager.getTown(townName);

        if (town == null) {
            return true;
        }

        // Vérifier les permissions d'interaction via Plot
        return plot.canInteract(playerUuid, town);
    }

    /**
     * Vérifie si un joueur peut attaquer des entités à cet emplacement
     */
    private boolean canDamageEntity(Player player, Location location) {
        // Admins bypass
        if (player.hasPermission("roleplaycity.town.admin")) {
            return true;
        }

        Plot plot = claimManager.getPlotAt(location);
        if (plot == null) {
            // Chunk non claimé, autorisé
            return true;
        }

        String townName = claimManager.getClaimOwner(location);
        Town town = townManager.getTown(townName);

        if (town == null) {
            return true;
        }

        // Seuls les membres de la ville peuvent attaquer des entités
        return town.isMember(player.getUniqueId());
    }

    /**
     * Vérifie si un bloc est interactif (nécessite des permissions spéciales)
     */
    private boolean isInteractiveBlock(Block block) {
        return switch (block.getType()) {
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX,
                 FURNACE, BLAST_FURNACE, SMOKER,
                 CRAFTING_TABLE, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL,
                 ENCHANTING_TABLE, BREWING_STAND,
                 DISPENSER, DROPPER, HOPPER,
                 LEVER, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON, BIRCH_BUTTON,
                 JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON,
                 OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR, ACACIA_DOOR,
                 DARK_OAK_DOOR, CRIMSON_DOOR, WARPED_DOOR, IRON_DOOR,
                 OAK_TRAPDOOR, SPRUCE_TRAPDOOR, BIRCH_TRAPDOOR, JUNGLE_TRAPDOOR,
                 ACACIA_TRAPDOOR, DARK_OAK_TRAPDOOR, CRIMSON_TRAPDOOR, WARPED_TRAPDOOR, IRON_TRAPDOOR,
                 OAK_FENCE_GATE, SPRUCE_FENCE_GATE, BIRCH_FENCE_GATE, JUNGLE_FENCE_GATE,
                 ACACIA_FENCE_GATE, DARK_OAK_FENCE_GATE, CRIMSON_FENCE_GATE, WARPED_FENCE_GATE,
                 ENDER_CHEST, BEACON, JUKEBOX, NOTE_BLOCK,
                 COMPARATOR, REPEATER, DAYLIGHT_DETECTOR,
                 LECTERN, LOOM, CARTOGRAPHY_TABLE, GRINDSTONE, STONECUTTER, SMITHING_TABLE -> true;
            default -> false;
        };
    }

    // ========== Protections basées sur les flags ==========

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Plot plot = claimManager.getPlotAt(block.getLocation());
            return plot != null && !plot.getFlag(PlotFlag.EXPLOSION);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (event.getCause() == BlockIgniteEvent.IgniteCause.SPREAD) {
            Plot plot = claimManager.getPlotAt(event.getBlock().getLocation());
            if (plot != null && !plot.getFlag(PlotFlag.FIRE_SPREAD)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() == Material.FIRE) {
            Plot plot = claimManager.getPlotAt(event.getBlock().getLocation());
            if (plot != null && !plot.getFlag(PlotFlag.FIRE_SPREAD)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.NATURAL) {
            LivingEntity entity = event.getEntity();
            if (entity instanceof Monster) {
                Plot plot = claimManager.getPlotAt(event.getLocation());
                if (plot != null && !plot.getFlag(PlotFlag.MOB_SPAWNING)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        Plot plot = claimManager.getPlotAt(event.getBlock().getLocation());
        if (plot != null && !plot.getFlag(PlotFlag.LEAF_DECAY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.getChangedType() == Material.FARMLAND) {
            Plot plot = claimManager.getPlotAt(event.getBlock().getLocation());
            if (plot != null && !plot.getFlag(PlotFlag.CROP_TRAMPLING)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        // Empêcher les Endermen de ramasser des blocs, etc.
        if (event.getEntity() instanceof Enderman || event.getEntity() instanceof Silverfish) {
            Plot plot = claimManager.getPlotAt(event.getBlock().getLocation());
            if (plot != null && !plot.getFlag(PlotFlag.ENTITY_GRIEFING)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Material mat = event.getBlock().getType();
        if (mat == Material.WATER || mat == Material.LAVA) {
            Plot plot = claimManager.getPlotAt(event.getToBlock().getLocation());
            if (plot != null && !plot.getFlag(PlotFlag.LIQUID_FLOW)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamagePlayer(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Player attacker) {
            Plot plot = claimManager.getPlotAt(victim.getLocation());
            if (plot != null && !plot.getFlag(PlotFlag.PVP)) {
                event.setCancelled(true);
                attacker.sendMessage(ChatColor.RED + "PVP désactivé sur ce plot.");
            }
        }
    }

    // ========== Protections basées sur les permissions détaillées ==========

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        Plot plot = claimManager.getPlotAt(location);
        if (plot != null && !plot.hasPermission(player.getUniqueId(), PlotPermission.ITEM_USE)) {
            String townName = claimManager.getClaimOwner(location);
            Town town = townManager.getTown(townName);

            if (town != null && !plot.canBuild(player.getUniqueId(), town)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser des seaux ici.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        Plot plot = claimManager.getPlotAt(location);
        if (plot != null && !plot.hasPermission(player.getUniqueId(), PlotPermission.ITEM_USE)) {
            String townName = claimManager.getClaimOwner(location);
            Town town = townManager.getTown(townName);

            if (town != null && !plot.canBuild(player.getUniqueId(), town)) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser des seaux ici.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageAnimal(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        Entity victim = event.getEntity();
        if (victim instanceof Animals || victim instanceof WaterMob) {
            Plot plot = claimManager.getPlotAt(victim.getLocation());
            if (plot != null && !plot.hasPermission(player.getUniqueId(), PlotPermission.ANIMALS)) {
                String townName = claimManager.getClaimOwner(victim.getLocation());
                Town town = townManager.getTown(townName);

                if (town != null && !plot.canBuild(player.getUniqueId(), town)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'attaquer des animaux ici.");
                }
            }
        }
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
