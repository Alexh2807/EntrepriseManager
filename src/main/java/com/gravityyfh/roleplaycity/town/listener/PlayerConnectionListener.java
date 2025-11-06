package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.manager.NotificationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener pour gérer les événements de connexion/déconnexion des joueurs.
 * Utilisé pour livrer les notifications en attente et la bannière de dettes.
 */
public class PlayerConnectionListener implements Listener {

    private final RoleplayCity plugin;
    private final NotificationManager notificationManager;

    public PlayerConnectionListener(RoleplayCity plugin) {
        this.plugin = plugin;
        this.notificationManager = plugin.getNotificationManager();
    }

    /**
     * Gère la connexion d'un joueur et déclenche les notifications différées.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (notificationManager != null) {
            notificationManager.checkPendingNotifications(player);
        }

        if (plugin.getDebtNotificationService() != null) {
            plugin.getDebtNotificationService().onPlayerLogin(player);
        }
    }
}
