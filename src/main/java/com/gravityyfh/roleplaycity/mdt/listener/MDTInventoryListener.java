package com.gravityyfh.roleplaycity.mdt.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.mdt.data.MDTGame;
import com.gravityyfh.roleplaycity.mdt.data.MDTGameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/**
 * Listener pour la protection de l'inventaire pendant le MDT Rush
 */
public class MDTInventoryListener implements Listener {
    private final RoleplayCity plugin;
    private final MDTRushManager manager;

    public MDTInventoryListener(RoleplayCity plugin, MDTRushManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /**
     * Empêcher le drop d'items en lobby/countdown
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();

        if (!manager.isPlayerInGame(player.getUniqueId())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null) return;

        // Empêcher le drop en lobby et countdown
        if (game.getState() == MDTGameState.LOBBY || game.getState() == MDTGameState.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêcher le pickup d'items en lobby/countdown (sauf générateurs en jeu)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onItemPickup(PlayerPickupItemEvent event) {
        Player player = event.getPlayer();

        if (!manager.isPlayerInGame(player.getUniqueId())) {
            return;
        }

        MDTGame game = manager.getCurrentGame();
        if (game == null) return;

        // Empêcher le pickup en lobby et countdown
        if (game.getState() == MDTGameState.LOBBY || game.getState() == MDTGameState.COUNTDOWN) {
            event.setCancelled(true);
        }
    }
}
