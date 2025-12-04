package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.util.PlayerNameResolver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener qui met à jour le cache des noms de joueurs quand un joueur se connecte.
 *
 * Cela permet de s'assurer que si un joueur a changé son pseudo sur Mojang,
 * le nouveau nom sera immédiatement disponible dans le cache.
 */
public class PlayerNameCacheListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Mettre à jour le cache avec le nom actuel du joueur
        PlayerNameResolver.updateCache(event.getPlayer());
    }
}
