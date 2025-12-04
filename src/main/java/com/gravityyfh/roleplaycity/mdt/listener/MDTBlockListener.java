package com.gravityyfh.roleplaycity.mdt.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTGameState;
import com.gravityyfh.roleplaycity.mdt.reset.BlockTracker;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.Iterator;

/**
 * Listener pour le tracking des blocs placés/cassés dans le MDT Rush
 */
public class MDTBlockListener implements Listener {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;
    private final BlockTracker blockTracker;

    public MDTBlockListener(RoleplayCity plugin, MDTRushManager manager, BlockTracker blockTracker) {
        this.plugin = plugin;
        this.manager = manager;
        this.blockTracker = blockTracker;
    }

    /**
     * Gestion du placement de blocs
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Vérifier si on est dans le monde MDT
        if (!isInMDTWorld(block.getWorld())) {
            return;
        }

        if (!manager.isPlayerInGame(player.getUniqueId())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null) return;

        // Autoriser le placement uniquement pendant le jeu
        if (game.getState() != MDTGameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // Tracker le bloc placé
        blockTracker.onBlockPlace(block.getLocation());
        plugin.getLogger().info("[MDT DEBUG] Bloc placé tracké: " + block.getType() + " à " +
                block.getX() + "," + block.getY() + "," + block.getZ() +
                " (Total: " + blockTracker.getPlayerPlacedBlockCount() + ")");
    }

    /**
     * Bloquer le placement pour les non-joueurs dans le monde MDT
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockPlaceRestrict(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!isInMDTWorld(block.getWorld())) {
            return;
        }

        // Si une partie est en cours mais le joueur n'est pas dedans
        if (manager.hasActiveGame() && !manager.isPlayerInGame(player.getUniqueId())) {
            // Vérifier si c'est un admin en mode setup
            if (!player.hasPermission("roleplaycity.mdt.setup")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Tu ne peux pas construire ici pendant une partie MDT!");
            }
        }
    }

    /**
     * Gestion de la destruction de blocs
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!isInMDTWorld(block.getWorld())) {
            return;
        }

        if (!manager.isPlayerInGame(player.getUniqueId())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null) return;

        // Autoriser la destruction uniquement pendant le jeu
        if (game.getState() != MDTGameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // Ne pas tracker les lits (gérés séparément)
        if (block.getType().name().endsWith("_BED")) {
            return;
        }

        // Tracker le bloc cassé
        blockTracker.onBlockBreak(block.getLocation(), block.getBlockData());
    }

    /**
     * Empêche la destruction des blocs blacklistés
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onUnbreakableBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        if (!isInMDTWorld(block.getWorld())) {
            return;
        }

        if (!manager.hasActiveGame()) return;

        // Vérifier si le bloc est dans la blacklist
        if (manager.getConfig().isUnbreakableBlock(block.getType())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "Ce bloc est indestructible!");
        }
    }

    /**
     * Bloquer la destruction pour les non-joueurs dans le monde MDT
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreakRestrict(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        if (!isInMDTWorld(block.getWorld())) {
            return;
        }

        // Si une partie est en cours mais le joueur n'est pas dedans
        if (manager.hasActiveGame() && !manager.isPlayerInGame(player.getUniqueId())) {
            if (!player.hasPermission("roleplaycity.mdt.setup")) {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "Tu ne peux pas casser de blocs ici pendant une partie MDT!");
            }
        }
    }

    /**
     * Gestion des explosions (TNT, dynamite)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!isInMDTWorld(event.getLocation().getWorld())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null || game.getState() != MDTGameState.PLAYING) {
            // Annuler les explosions hors partie
            event.setCancelled(true);
            return;
        }

        // Tracker tous les blocs détruits par l'explosion
        Iterator<Block> iterator = event.blockList().iterator();
        while (iterator.hasNext()) {
            Block block = iterator.next();

            // Protéger les blocs de la blacklist (incluant obsidienne, bedrock, etc.)
            if (manager.getConfig().isUnbreakableBlock(block.getType())) {
                iterator.remove();
                continue;
            }

            // Ne pas tracker les lits ici (gérés par MDTBedListener)
            if (block.getType().name().endsWith("_BED")) {
                iterator.remove();
                continue;
            }

            // Tracker le bloc
            blockTracker.onBlockExplode(block.getLocation(), block.getBlockData());
        }
    }

    /**
     * Vérifie si le monde est le monde MDT
     */
    private boolean isInMDTWorld(World world) {
        if (world == null) return false;
        String mdtWorldName = manager.getConfig().getWorldName();
        return world.getName().equalsIgnoreCase(mdtWorldName);
    }
}
