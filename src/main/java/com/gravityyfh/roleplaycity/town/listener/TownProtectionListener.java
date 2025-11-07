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

        // ⚠️ NOUVEAU SYSTÈME : Gestion RenterBlockTracker avec PlotGroup autonome
        String townName = claimManager.getClaimOwner(location);
        if (townName != null) {
            Town town = townManager.getTown(townName);
            if (town != null) {
                TerritoryEntity territory = town.getTerritoryAt(location);
                if (territory != null) {
                    UUID playerUuid = player.getUniqueId();
                    UUID renterUuid = territory.getRenterUuid();

                    // CAS 1 : Joueur est locataire - peut seulement casser SES blocs
                    if (territory.isRentedBy(playerUuid)) {
                        if (!territory.getRenterBlockTracker().canRenterBreak(playerUuid, location)) {
                            event.setCancelled(true);
                            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas casser les blocs existants de cette location.");
                            player.sendMessage(ChatColor.GRAY + "Vous pouvez seulement casser les blocs que vous avez placés.");
                            return;
                        }
                        // Retirer le bloc du tracker après cassage
                        territory.getRenterBlockTracker().removeBlock(playerUuid, location);
                        townManager.saveTownsNow();
                        return;
                    }

                    // CAS 2 : Terrain loué à quelqu'un d'autre - propriétaire ne peut pas casser blocs locataire
                    if (renterUuid != null && !territory.isRentedBy(playerUuid)) {
                        if (territory.getRenterBlockTracker().canRenterBreak(renterUuid, location)) {
                            event.setCancelled(true);
                            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas détruire les constructions de votre locataire.");
                            player.sendMessage(ChatColor.GRAY + "Attendez la fin de la location pour modifier ces blocs.");
                            return;
                        }
                    }
                }
            }
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

        // ⚠️ NOUVEAU SYSTÈME : Tracker les blocs placés par les locataires
        String townName = claimManager.getClaimOwner(location);
        if (townName != null) {
            Town town = townManager.getTown(townName);
            if (town != null) {
                TerritoryEntity territory = town.getTerritoryAt(location);
                if (territory != null) {
                    UUID playerUuid = player.getUniqueId();

                    // Si le joueur est locataire, enregistrer le bloc placé
                    if (territory.isRentedBy(playerUuid)) {
                        territory.getRenterBlockTracker().addBlock(playerUuid, location);
                        townManager.saveTownsNow();
                    }
                }
            }
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
     * NOUVEAU SYSTÈME : Utilise getTerritoryAt() pour gérer Plot ET PlotGroup autonomes
     */
    private boolean canBuild(Player player, Location location) {
        // Admins bypass
        if (player.hasPermission("roleplaycity.town.admin")) {
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

        // ⚠️ NOUVEAU : Utiliser getTerritoryAt() qui retourne Plot OU PlotGroup
        TerritoryEntity territory = town.getTerritoryAt(location);
        if (territory == null) {
            // Pas de terrain trouvé (ne devrait pas arriver si townName existe)
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        // Utiliser l'interface unifiée TerritoryEntity
        return territory.canBuild(playerUuid, town);
    }

    /**
     * Vérifie si un joueur peut construire dans un groupe de terrains
     */
    private boolean canBuildInGroup(UUID playerUuid, PlotGroup group, Town town) {
        // Propriétaire du groupe peut construire
        if (group.isOwnedBy(playerUuid)) {
            return true;
        }

        // Locataire du groupe peut construire
        if (group.isRentedBy(playerUuid)) {
            return true;
        }

        // Maire et Adjoint peuvent toujours construire
        TownRole role = town.getMemberRole(playerUuid);
        if (role == TownRole.MAIRE || role == TownRole.ADJOINT) {
            return true;
        }

        return false;
    }

    /**
     * Vérifie si un joueur peut interagir à cet emplacement
     * NOUVEAU SYSTÈME : Utilise getTerritoryAt() pour gérer Plot ET PlotGroup autonomes
     */
    private boolean canInteract(Player player, Location location) {
        // Admins bypass
        if (player.hasPermission("roleplaycity.town.admin")) {
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

        // ⚠️ NOUVEAU : Utiliser getTerritoryAt() qui retourne Plot OU PlotGroup
        TerritoryEntity territory = town.getTerritoryAt(location);
        if (territory == null) {
            // Pas de terrain trouvé
            return true;
        }

        UUID playerUuid = player.getUniqueId();

        // Utiliser l'interface unifiée TerritoryEntity
        return territory.canInteract(playerUuid, town);
    }

    /**
     * Vérifie si un joueur peut interagir dans un groupe de terrains
     */
    private boolean canInteractInGroup(UUID playerUuid, PlotGroup group, Town town) {
        // Propriétaire du groupe peut interagir
        if (group.isOwnedBy(playerUuid)) {
            return true;
        }

        // Locataire du groupe peut interagir
        if (group.isRentedBy(playerUuid)) {
            return true;
        }

        // Maire et Adjoint peuvent toujours interagir
        TownRole role = town.getMemberRole(playerUuid);
        if (role == TownRole.MAIRE || role == TownRole.ADJOINT) {
            return true;
        }

        return false;
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
        if (plot != null) {
            String townName = claimManager.getClaimOwner(location);
            Town town = townManager.getTown(townName);

            if (town != null) {
                // SYSTÈME AUTOMATIQUE : Vérifier les permissions au niveau du groupe si applicable
                if (!hasPermissionAutomatic(player.getUniqueId(), plot, PlotPermission.ITEM_USE, town)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser des seaux ici.");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        Plot plot = claimManager.getPlotAt(location);
        if (plot != null) {
            String townName = claimManager.getClaimOwner(location);
            Town town = townManager.getTown(townName);

            if (town != null) {
                // SYSTÈME AUTOMATIQUE : Vérifier les permissions au niveau du groupe si applicable
                if (!hasPermissionAutomatic(player.getUniqueId(), plot, PlotPermission.ITEM_USE, town)) {
                    event.setCancelled(true);
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser des seaux ici.");
                }
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
            if (plot != null) {
                String townName = claimManager.getClaimOwner(victim.getLocation());
                Town town = townManager.getTown(townName);

                if (town != null) {
                    // SYSTÈME AUTOMATIQUE : Vérifier les permissions au niveau du groupe si applicable
                    if (!hasPermissionAutomatic(player.getUniqueId(), plot, PlotPermission.ANIMALS, town)) {
                        event.setCancelled(true);
                        player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'attaquer des animaux ici.");
                    }
                }
            }
        }
    }

    /**
     * Vérifie automatiquement une permission au niveau du plot OU du groupe
     */
    private boolean hasPermissionAutomatic(UUID playerUuid, Plot plot, PlotPermission permission, Town town) {
        // SYSTÈME AUTOMATIQUE : Vérifier si le plot fait partie d'un groupe
        PlotGroup group = town.findPlotGroupByPlot(plot);
        if (group != null) {
            // Le plot est dans un groupe : vérifier les permissions du GROUPE
            return hasPermissionInGroup(playerUuid, group, permission);
        }

        // Plot individuel : vérifier les permissions normales
        return plot.hasPermission(playerUuid, permission) || plot.canBuild(playerUuid, town);
    }

    /**
     * Vérifie si un joueur a une permission dans un groupe de terrains
     */
    private boolean hasPermissionInGroup(UUID playerUuid, PlotGroup group, PlotPermission permission) {
        // Propriétaire et locataire ont toutes les permissions
        if (group.isOwnedBy(playerUuid) || group.isRentedBy(playerUuid)) {
            return true;
        }

        // TODO: Ajouter système de joueurs de confiance au niveau des groupes
        // TODO: Ajouter système de permissions individuelles au niveau des groupes

        return false;
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

    /**
     * Trouve le PlotGroup auquel appartient un Plot (si existe)
     */
    private PlotGroup findPlotGroup(Plot plot) {
        String townName = plot.getTownName();
        Town town = townManager.getTown(townName);

        if (town == null) {
            return null;
        }

        // Parcourir tous les groupes de la ville
        for (PlotGroup group : town.getPlotGroups().values()) {
            if (group.containsPlot(plot)) {
                return group;
            }
        }

        return null;
    }
}
