package com.gravityyfh.roleplaycity.town.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.town.manager.NotificationManager.Notification;
import com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Service de persistance SQLite pour les notifications
 */
public class NotificationPersistenceService {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public NotificationPersistenceService(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Charge les notifications non lues
     */
    public Map<UUID, Queue<Notification>> loadOfflineNotifications() {
        Map<UUID, Queue<Notification>> notifications = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection()) {
            String sql = "SELECT * FROM notifications WHERE is_read = 0 ORDER BY timestamp ASC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    Notification notif = loadNotification(rs);

                    notifications.computeIfAbsent(playerUuid, k -> new LinkedList<>()).add(notif);
                }
            }

            int totalNotifs = notifications.values().stream().mapToInt(Queue::size).sum();
            plugin.getLogger().info("[Notifications] Chargé " + totalNotifs + " notifications non lues depuis SQLite");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Notifications] Erreur chargement notifications", e);
        }

        return notifications;
    }

    /**
     * Charge l'historique des notifications
     */
    public Map<UUID, List<Notification>> loadNotificationHistory() {
        Map<UUID, List<Notification>> history = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection()) {
            String sql = "SELECT * FROM notifications WHERE is_read = 1 ORDER BY timestamp DESC LIMIT 1000";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    Notification notif = loadNotification(rs);

                    history.computeIfAbsent(playerUuid, k -> new ArrayList<>()).add(notif);
                }
            }

            int totalNotifs = history.values().stream().mapToInt(List::size).sum();
            plugin.getLogger().info("[Notifications] Chargé " + totalNotifs + " notifications lues depuis SQLite");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Notifications] Erreur chargement historique", e);
        }

        return history;
    }

    private Notification loadNotification(ResultSet rs) throws SQLException {
        NotificationType type = NotificationType.valueOf(rs.getString("type"));
        String title = rs.getString("title");
        String message = rs.getString("message");
        LocalDateTime timestamp = LocalDateTime.parse(rs.getString("timestamp"), DATE_FORMAT);
        boolean isRead = rs.getInt("is_read") == 1;

        return new Notification(type, title, message, timestamp, isRead);
    }

    /**
     * Sauvegarde toutes les notifications (Remplace les données existantes)
     */
    public void saveAllNotifications(Map<UUID, Queue<Notification>> offline, Map<UUID, List<Notification>> history) {
        try {
            connectionManager.executeTransaction(conn -> {
                // 1. Tout supprimer (approche brutale mais sûre pour la synchro mémoire -> DB style YAML)
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM notifications");
                }

                String sql = "INSERT INTO notifications (player_uuid, type, title, message, timestamp, is_read, read_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    // Offline
                    for (Map.Entry<UUID, Queue<Notification>> entry : offline.entrySet()) {
                        String uuid = entry.getKey().toString();
                        for (Notification notif : entry.getValue()) {
                            addBatch(stmt, uuid, notif, false);
                        }
                    }

                    // History
                    for (Map.Entry<UUID, List<Notification>> entry : history.entrySet()) {
                        String uuid = entry.getKey().toString();
                        for (Notification notif : entry.getValue()) {
                            addBatch(stmt, uuid, notif, true);
                        }
                    }

                    stmt.executeBatch();
                }
            });
            
            int total = offline.values().stream().mapToInt(Queue::size).sum() + 
                        history.values().stream().mapToInt(List::size).sum();
            plugin.getLogger().info("[Notifications] Sauvegardé " + total + " notifications (Full Sync)");
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Notifications] Erreur sauvegarde globale", e);
        }
    }

    private void addBatch(PreparedStatement stmt, String uuid, Notification notif, boolean read) throws SQLException {
        stmt.setString(1, uuid);
        stmt.setString(2, notif.getType().name());
        stmt.setString(3, notif.getTitle());
        stmt.setString(4, notif.getMessage());
        stmt.setString(5, notif.getTimestamp().format(DATE_FORMAT));
        stmt.setInt(6, read ? 1 : 0);
        stmt.setString(7, read ? LocalDateTime.now().format(DATE_FORMAT) : null);
        stmt.addBatch();
    }

    /**
     * Sauvegarde une notification
     */
    public void saveNotification(UUID playerUuid, Notification notification) {
        try {
            String sql = "INSERT INTO notifications (" +
                "player_uuid, type, title, message, timestamp, is_read, read_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?)";

            connectionManager.executeUpdate(sql,
                playerUuid.toString(),
                notification.getType().name(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTimestamp().format(DATE_FORMAT),
                notification.isRead() ? 1 : 0,
                notification.isRead() ? LocalDateTime.now().format(DATE_FORMAT) : null
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Notifications] Erreur sauvegarde notification", e);
        }
    }

    /**
     * Marque une notification comme lue
     */
    public void markAsRead(UUID playerUuid, LocalDateTime notifTimestamp) {
        try {
            String sql = "UPDATE notifications SET is_read = 1, read_at = ? " +
                "WHERE player_uuid = ? AND timestamp = ?";

            connectionManager.executeUpdate(sql,
                LocalDateTime.now().format(DATE_FORMAT),
                playerUuid.toString(),
                notifTimestamp.format(DATE_FORMAT)
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Notifications] Erreur marquage lu", e);
        }
    }

    /**
     * Nettoie les vieilles notifications (> 30 jours)
     */
    public void cleanOldNotifications() {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            String sql = "DELETE FROM notifications WHERE is_read = 1 AND timestamp < ?";

            int deleted = connectionManager.executeUpdate(sql, cutoff.format(DATE_FORMAT));
            if (deleted > 0) {
                plugin.getLogger().info("[Notifications] Supprimé " + deleted + " vieilles notifications");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Notifications] Erreur nettoyage", e);
        }
    }
}