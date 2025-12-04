package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gère les sessions d'administration de villes pour les admins globaux.
 * Permet à un admin de gérer une ville dont il n'est pas membre.
 */
public class AdminTownSessionManager implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;

    // UUID admin -> nom de la ville ciblée
    private final Map<UUID, String> adminSessions;

    public AdminTownSessionManager(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.adminSessions = new HashMap<>();
    }

    /**
     * Démarre une session admin sur une ville
     * @param admin Le joueur admin
     * @param townName Le nom de la ville à administrer
     * @return true si la session a été créée avec succès
     */
    public boolean startSession(Player admin, String townName) {
        if (!admin.hasPermission("roleplaycity.admin") && !admin.isOp()) {
            admin.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande.");
            return false;
        }

        // Vérifier que la ville existe
        if (townManager.getTown(townName) == null) {
            admin.sendMessage(ChatColor.RED + "La ville '" + townName + "' n'existe pas.");
            return false;
        }

        // Enregistrer la session
        String previousTown = adminSessions.put(admin.getUniqueId(), townName);

        if (previousTown != null) {
            plugin.getLogger().info("[AdminSession] " + admin.getName() + " a changé de ville: " + previousTown + " -> " + townName);
        } else {
            plugin.getLogger().info("[AdminSession] " + admin.getName() + " a démarré une session admin sur: " + townName);
        }

        admin.sendMessage(ChatColor.GREEN + "✓ Session admin activée sur la ville: " + ChatColor.GOLD + townName);
        admin.sendMessage(ChatColor.GRAY + "Vous pouvez maintenant gérer cette ville comme si vous étiez le Maire.");
        admin.sendMessage(ChatColor.GRAY + "Tapez " + ChatColor.YELLOW + "/ville admin town exit" + ChatColor.GRAY + " pour quitter.");

        return true;
    }

    /**
     * Termine la session admin du joueur
     * @param admin Le joueur admin
     */
    public void endSession(Player admin) {
        String townName = adminSessions.remove(admin.getUniqueId());

        if (townName != null) {
            plugin.getLogger().info("[AdminSession] " + admin.getName() + " a terminé sa session admin sur: " + townName);
            admin.sendMessage(ChatColor.YELLOW + "✓ Session admin terminée pour la ville: " + townName);
        } else {
            admin.sendMessage(ChatColor.GRAY + "Vous n'avez pas de session admin active.");
        }
    }

    /**
     * Récupère la ville ciblée par l'admin en session
     * @param playerUuid L'UUID du joueur
     * @return Le nom de la ville ciblée, ou null si pas de session active
     */
    public String getAdminTargetTown(UUID playerUuid) {
        return adminSessions.get(playerUuid);
    }

    /**
     * Vérifie si le joueur a une session admin active
     * @param playerUuid L'UUID du joueur
     * @return true si une session est active
     */
    public boolean hasActiveSession(UUID playerUuid) {
        return adminSessions.containsKey(playerUuid);
    }

    /**
     * Vérifie si le joueur est en mode admin override pour une ville spécifique
     * @param playerUuid L'UUID du joueur
     * @param townName Le nom de la ville
     * @return true si l'admin a une session active sur cette ville
     */
    public boolean isAdminOverride(UUID playerUuid, String townName) {
        String targetTown = adminSessions.get(playerUuid);
        return targetTown != null && targetTown.equalsIgnoreCase(townName);
    }

    /**
     * Vérifie si le joueur a une session admin active (peu importe la ville)
     * Utile pour les vérifications générales où on ne connaît pas encore la ville
     * @param player Le joueur
     * @return true si une session est active
     */
    public boolean isInAdminMode(Player player) {
        return hasActiveSession(player.getUniqueId());
    }

    /**
     * Nettoie la session à la déconnexion
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        String townName = adminSessions.remove(event.getPlayer().getUniqueId());
        if (townName != null) {
            plugin.getLogger().info("[AdminSession] Session admin de " + event.getPlayer().getName() +
                " sur " + townName + " terminée (déconnexion)");
        }
    }

    /**
     * Retourne le nombre de sessions admin actives
     */
    public int getActiveSessionCount() {
        return adminSessions.size();
    }
}
