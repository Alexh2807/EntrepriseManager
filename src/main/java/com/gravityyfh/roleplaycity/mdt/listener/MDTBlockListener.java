package com.gravityyfh.roleplaycity.mdt.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTGameState;
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
 * Listener pour les blocs dans le MDT Rush
 * Note: Avec le nouveau système FAWE, nous n'avons plus besoin de tracker les blocs individuellement
 * Le système FAWE gère la sauvegarde/restauration complète de la map
 */
public class MDTBlockListener implements Listener {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;

    public MDTBlockListener(RoleplayCity plugin, MDTRushManager manager) {
        this.plugin = plugin;
        this.manager = manager;
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

        // Vérification de la hauteur de build
        if (block.getY() > manager.getConfig().getMaxBuildHeight()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Tu ne peux pas construire au-dessus de la hauteur maximale (Y=" +
                manager.getConfig().getMaxBuildHeight() + ")!");
            return;
        }

        // Note: Le tracking des blocs n'est plus nécessaire avec FAWE
        plugin.getLogger().info("[MDT DEBUG] Bloc placé: " + block.getType() + " à " +
                block.getX() + "," + block.getY() + "," + block.getZ());
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
                return;
            }
        }

        // Vérification de la hauteur de build pour tout le monde dans le monde MDT
        if (block.getY() > manager.getConfig().getMaxBuildHeight()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Tu ne peux pas construire au-dessus de la hauteur maximale (Y=" +
                manager.getConfig().getMaxBuildHeight() + ")!");
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

        // Note: Le tracking des blocs n'est plus nécessaire avec FAWE
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
     * Protection des blocs incassables contre les explosions (TNT, dynamite, etc.)
     * Priorité HIGH pour bloquer avant que les dégâts ne soient appliqués
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onExplosionBlockProtection(EntityExplodeEvent event) {
        if (!isInMDTWorld(event.getLocation().getWorld())) {
            return;
        }

        // Protection complète : si une partie n'est pas en cours, bloquer toutes les explosions
        MDTGame game = manager.getCurrentGame();
        if (game == null || game.getState() != MDTGameState.PLAYING) {
            event.setCancelled(true);
            return;
        }

        // Filtrer les blocs protégés de la liste des explosions
        Iterator<Block> iterator = event.blockList().iterator();
        int blockedCount = 0;

        while (iterator.hasNext()) {
            Block block = iterator.next();

            // Protéger les blocs incassables CONFIGURÉS (ceux dans mdt.yml)
            if (manager.getConfig().isUnbreakableBlock(block.getType())) {
                iterator.remove();
                blockedCount++;
                continue;
            }

            // Ne pas détruire les lits par explosion (gérés par MDTBedListener)
            if (block.getType().name().endsWith("_BED")) {
                iterator.remove();
                blockedCount++;
                continue;
            }
        }

        // Logger la protection si des blocs ont été protégés
        if (blockedCount > 0) {
            plugin.getLogger().info("[MDT] ✅ " + blockedCount + " blocs protégés contre l'explosion");
        }
    }

    /**
     * Bloquer complètement les explosions pour les non-joueurs dans le monde MDT
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onExplosionRestriction(EntityExplodeEvent event) {
        if (!isInMDTWorld(event.getLocation().getWorld())) {
            return;
        }

        // Si une partie est en cours mais que l'explosion ne vient pas d'un joueur
        if (manager.hasActiveGame()) {
            // Vérifier si l'explosion vient d'une entité liée à un joueur (TNT, dynamite custom, etc.)
            boolean fromPlayer = false;

            if (event.getEntity() != null) {
                // Vérifier si c'est une TNT ou autre explosive lancée par un joueur
                if (event.getEntity().getCustomName() != null ||
                    event.getEntity().getMetadata("player_owner") != null) {
                    fromPlayer = true;
                }
            }

            // Si l'explosion ne vient pas d'un joueur et qu'une partie est en cours, la bloquer
            if (!fromPlayer) {
                event.setCancelled(true);
                return;
            }
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
