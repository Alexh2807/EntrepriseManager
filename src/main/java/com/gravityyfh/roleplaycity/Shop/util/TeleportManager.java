package com.gravityyfh.roleplaycity.shop.util;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire de téléportations avec délai et vérification de mouvement
 */
public class TeleportManager {
    private final RoleplayCity plugin;
    private final Map<UUID, TeleportRequest> pendingTeleports = new HashMap<>();

    // Configuration
    private static final int TELEPORT_DELAY_SECONDS = 5;
    private static final double MAX_MOVEMENT_DISTANCE = 0.3; // Distance maximale autorisée en blocs

    public TeleportManager(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    /**
     * Initie une téléportation avec délai
     * @param player Le joueur à téléporter
     * @param destination La destination
     * @param reason Raison de la téléportation (pour le message)
     */
    public void initiateTeleport(Player player, Location destination, String reason) {
        // Vérifier si une téléportation est déjà en cours
        if (pendingTeleports.containsKey(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "✗ Une téléportation est déjà en cours!");
            return;
        }

        // Sauvegarder la position de départ
        Location startLocation = player.getLocation().clone();

        // Créer la requête de téléportation
        TeleportRequest request = new TeleportRequest(player.getUniqueId(), startLocation, destination, reason);
        pendingTeleports.put(player.getUniqueId(), request);

        // Message initial
        player.sendMessage(ChatColor.YELLOW + "⏳ Téléportation dans " + TELEPORT_DELAY_SECONDS + " secondes...");
        player.sendMessage(ChatColor.GRAY + "Raison: " + reason);
        player.sendMessage(ChatColor.RED + "⚠ Ne bougez pas!");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

        // Démarrer le compte à rebours
        startCountdown(request);
    }

    /**
     * Démarre le compte à rebours de téléportation
     */
    private void startCountdown(TeleportRequest request) {
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            int countdown = TELEPORT_DELAY_SECONDS;

            @Override
            public void run() {
                // Vérifier que la requête existe toujours
                if (!pendingTeleports.containsKey(request.playerId)) {
                    return; // Téléportation annulée
                }

                Player player = Bukkit.getPlayer(request.playerId);
                if (player == null || !player.isOnline()) {
                    cancelTeleport(request.playerId, "Joueur déconnecté");
                    return;
                }

                // Vérifier le mouvement
                if (hasPlayerMoved(player.getLocation(), request.startLocation)) {
                    cancelTeleport(request.playerId, "Vous avez bougé!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                countdown--;

                if (countdown > 0) {
                    // Continuer le compte à rebours
                    if (countdown <= 3) {
                        // Notification sonore pour les dernières secondes
                        player.sendMessage(ChatColor.YELLOW + "⏳ " + countdown + "...");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f + (0.2f * (3 - countdown)));
                    }

                    // Planifier la prochaine vérification
                    Bukkit.getScheduler().runTaskLater(plugin, this, 20L); // 1 seconde
                } else {
                    // Téléportation finale
                    executeTeleport(request);
                }
            }
        }, 20L); // Première vérification après 1 seconde
    }

    /**
     * Exécute la téléportation finale
     */
    private void executeTeleport(TeleportRequest request) {
        Player player = Bukkit.getPlayer(request.playerId);
        if (player == null || !player.isOnline()) {
            cancelTeleport(request.playerId, "Joueur déconnecté");
            return;
        }

        // Vérification finale du mouvement
        if (hasPlayerMoved(player.getLocation(), request.startLocation)) {
            cancelTeleport(request.playerId, "Vous avez bougé!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Téléportation réussie
        player.teleport(request.destination);
        player.sendMessage(ChatColor.GREEN + "✓ Téléportation réussie!");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);

        // Nettoyer la requête
        pendingTeleports.remove(request.playerId);
    }

    /**
     * Vérifie si le joueur a bougé
     */
    private boolean hasPlayerMoved(Location current, Location start) {
        // Vérifier si le monde est différent
        if (!current.getWorld().equals(start.getWorld())) {
            return true;
        }

        // Calculer la distance (ignorer la rotation)
        double distance = current.distance(start);
        return distance > MAX_MOVEMENT_DISTANCE;
    }

    /**
     * Annule une téléportation en cours
     */
    public void cancelTeleport(UUID playerId, String reason) {
        TeleportRequest request = pendingTeleports.remove(playerId);
        if (request != null) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                player.sendMessage(ChatColor.RED + "✗ Téléportation annulée: " + reason);
            }
        }
    }

    /**
     * Vérifie si un joueur a une téléportation en cours
     */
    public boolean hasPendingTeleport(UUID playerId) {
        return pendingTeleports.containsKey(playerId);
    }

    /**
     * Nettoie toutes les téléportations en attente (lors du disable du plugin)
     */
    public void cleanup() {
        for (UUID playerId : new HashMap<>(pendingTeleports).keySet()) {
            cancelTeleport(playerId, "Plugin en cours de désactivation");
        }
        pendingTeleports.clear();
    }

    /**
     * Classe interne représentant une requête de téléportation
     */
    private static class TeleportRequest {
        final UUID playerId;
        final Location startLocation;
        final Location destination;
        final String reason;
        final long timestamp;

        TeleportRequest(UUID playerId, Location startLocation, Location destination, String reason) {
            this.playerId = playerId;
            this.startLocation = startLocation;
            this.destination = destination;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
        }
    }
}
