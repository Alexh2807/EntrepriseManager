package com.gravityyfh.roleplaycity.identity.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.identity.service.IdentityDisplayService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Listener pour appliquer le nom d'identité aux joueurs
 * - Au login, applique le nom si le joueur a une identité
 * - Au logout, nettoie la team scoreboard
 */
public class IdentityDisplayListener implements Listener {

    private final RoleplayCity plugin;
    private final IdentityDisplayService displayService;

    public IdentityDisplayListener(RoleplayCity plugin, IdentityDisplayService displayService) {
        this.plugin = plugin;
        this.displayService = displayService;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Appliquer le nom d'identité avec un léger délai pour s'assurer que tout est chargé
        new BukkitRunnable() {
            @Override
            public void run() {
                if (event.getPlayer().isOnline()) {
                    displayService.applyIdentityName(event.getPlayer());
                }
            }
        }.runTaskLater(plugin, 5L); // 0.25 seconde de délai
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Nettoyer la team du joueur qui part (optionnel, évite l'accumulation)
        // Note: On ne fait rien ici car la team peut rester pour quand le joueur revient
        // displayService.resetToOriginalName(event.getPlayer());
    }
}
