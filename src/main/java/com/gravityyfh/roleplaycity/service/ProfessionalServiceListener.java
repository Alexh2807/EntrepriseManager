package com.gravityyfh.roleplaycity.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener pour gérer les événements de connexion/déconnexion
 * et restaurer le service professionnel
 */
public class ProfessionalServiceListener implements Listener {

    private final RoleplayCity plugin;
    private final ProfessionalServiceManager serviceManager;

    public ProfessionalServiceListener(RoleplayCity plugin, ProfessionalServiceManager serviceManager) {
        this.plugin = plugin;
        this.serviceManager = serviceManager;
    }

    /**
     * Restaure le service à la connexion du joueur
     * Vérifie d'abord si le service n'a pas expiré ou si le rôle est toujours valide
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Délai pour laisser le temps aux autres systèmes de s'initialiser
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            // Vérifier si le joueur avait un service actif
            if (!serviceManager.isInAnyService(player.getUniqueId())) {
                return;
            }

            // 1. Vérifier si le service a expiré (déconnexion > 10 minutes)
            boolean expired = serviceManager.checkAndDeactivateIfExpired(player);
            if (expired) {
                return; // Service désactivé, ne pas restaurer
            }

            // 2. Vérifier si le joueur a toujours le rôle requis
            boolean roleInvalid = serviceManager.checkRoleAndDeactivate(player);
            if (roleInvalid) {
                return; // Service désactivé, ne pas restaurer
            }

            // 3. Tout est OK, restaurer le service normalement
            serviceManager.restoreServiceOnJoin(player);

        }, 40L); // 2 secondes de délai
    }

    /**
     * Nettoie les effets visuels à la déconnexion
     * et enregistre le timestamp de déconnexion pour le timeout automatique
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Enregistrer le timestamp de déconnexion (pour le timeout de 10 min)
        serviceManager.recordDisconnectTime(player.getUniqueId());

        // Nettoyer la BossBar (mais garder les données)
        serviceManager.cleanupOnQuit(player.getUniqueId());
    }
}
