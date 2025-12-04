package com.gravityyfh.roleplaycity.police.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.police.data.PrisonData;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Service de persistance SQLite pour le système de prison
 */
public class PrisonPersistenceService {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public PrisonPersistenceService(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Charge les joueurs emprisonnés
     */
    public Map<UUID, PrisonData> loadImprisonedPlayers() {
        Map<UUID, PrisonData> imprisoned = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM imprisoned_players")) {

            while (rs.next()) {
                UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                // String playerName = rs.getString("player_name"); // Not stored in PrisonData currently
                String townName = rs.getString("town_name");
                String plotIdentifier = rs.getString("plot_identifier");
                String reason = rs.getString("reason");
                int durationMinutes = rs.getInt("duration_minutes");
                LocalDateTime startTime = LocalDateTime.parse(rs.getString("start_time"), DATE_FORMAT);
                // LocalDateTime endTime = LocalDateTime.parse(rs.getString("end_time"), DATE_FORMAT);
                UUID officerUuid = UUID.fromString(rs.getString("arresting_officer_uuid"));
                // String officerName = rs.getString("arresting_officer_name"); // Not stored in PrisonData

                PrisonData data = new PrisonData(
                    playerUuid, townName, plotIdentifier,
                    startTime, durationMinutes,
                    reason, officerUuid
                );

                imprisoned.put(playerUuid, data);
            }

            plugin.getLogger().info("[Prison] Chargé " + imprisoned.size() + " joueurs emprisonnés depuis SQLite");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Prison] Erreur chargement emprisonnés", e);
        }

        return imprisoned;
    }

    /**
     * Sauvegarde un joueur emprisonné
     */
    public void saveImprisonedPlayer(PrisonData data) {
        try {
            String playerName = Bukkit.getOfflinePlayer(data.getPlayerUuid()).getName();
            String officerName = Bukkit.getOfflinePlayer(data.getImprisonedBy()).getName();
            LocalDateTime endTime = data.getImprisonmentStart().plusMinutes(data.getCurrentDurationMinutes());

            String sql = "INSERT OR REPLACE INTO imprisoned_players (" +
                "player_uuid, player_name, town_name, plot_identifier, reason, duration_minutes, " +
                "start_time, end_time, arresting_officer_uuid, arresting_officer_name" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            connectionManager.executeUpdate(sql,
                data.getPlayerUuid().toString(),
                playerName != null ? playerName : "Unknown",
                data.getTownName(),
                data.getPlotIdentifier(),
                data.getReason(),
                data.getCurrentDurationMinutes(),
                data.getImprisonmentStart().format(DATE_FORMAT),
                endTime.format(DATE_FORMAT),
                data.getImprisonedBy().toString(),
                officerName != null ? officerName : "Unknown"
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Prison] Erreur sauvegarde emprisonné", e);
        }
    }

    /**
     * Supprime un joueur emprisonné (libération)
     */
    public void releasePlayer(UUID playerUuid) {
        try {
            connectionManager.executeUpdate("DELETE FROM imprisoned_players WHERE player_uuid = ?",
                playerUuid.toString());
            plugin.getLogger().info("[Prison] Joueur libéré: " + playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Prison] Erreur libération", e);
        }
    }

    /**
     * Charge les joueurs menottés
     * @return Map UUID -> Santé des menottes
     */
    public Map<UUID, Double> loadHandcuffedPlayers() {
        Map<UUID, Double> handcuffed = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM handcuffed_players")) {

            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    double health = rs.getDouble("health");
                    handcuffed.put(uuid, health);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[Police] UUID invalide dans handcuffed_players: " + rs.getString("player_uuid"));
                }
            }

            if (!handcuffed.isEmpty()) {
                plugin.getLogger().info("[Police] Chargé " + handcuffed.size() + " joueurs menottés");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Police] Erreur chargement menottes", e);
        }

        return handcuffed;
    }

    /**
     * Sauvegarde tous les joueurs menottés
     */
    public void saveHandcuffedPlayers(Map<UUID, Double> handcuffedData) {
        try {
            connectionManager.executeTransaction(conn -> {
                // 1. Vider la table
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM handcuffed_players");
                }

                if (handcuffedData.isEmpty()) return;

                // 2. Insérer les données
                String sql = "INSERT INTO handcuffed_players (player_uuid, health, timestamp) VALUES (?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    long now = System.currentTimeMillis();

                    for (Map.Entry<UUID, Double> entry : handcuffedData.entrySet()) {
                        stmt.setString(1, entry.getKey().toString());
                        stmt.setDouble(2, entry.getValue());
                        stmt.setLong(3, now);
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }
            });
            
            // plugin.getLogger().info("[Police] Sauvegardé " + handcuffedData.size() + " joueurs menottés");
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Police] Erreur sauvegarde menottes", e);
        }
    }

    /**
     * Charge les joueurs tasés
     * @return Map UUID -> Expiration Timestamp
     */
    public Map<UUID, Long> loadTasedPlayers() {
        Map<UUID, Long> tased = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM tased_players")) {

            long now = System.currentTimeMillis();
            
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    long expiration = rs.getLong("expiration_timestamp");
                    
                    // Ne charger que si encore valide
                    if (expiration > now) {
                        tased.put(uuid, expiration);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[Police] UUID invalide dans tased_players: " + rs.getString("player_uuid"));
                }
            }

            if (!tased.isEmpty()) {
                plugin.getLogger().info("[Police] Chargé " + tased.size() + " joueurs tasés");
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Police] Erreur chargement tased_players", e);
        }

        return tased;
    }

    /**
     * Sauvegarde tous les joueurs tasés
     */
    public void saveTasedPlayers(Map<UUID, Long> tasedData) {
        try {
            connectionManager.executeTransaction(conn -> {
                // 1. Vider la table
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM tased_players");
                }

                if (tasedData.isEmpty()) return;

                // 2. Insérer les données
                String sql = "INSERT INTO tased_players (player_uuid, expiration_timestamp) VALUES (?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    long now = System.currentTimeMillis();

                    for (Map.Entry<UUID, Long> entry : tasedData.entrySet()) {
                        // Ne sauvegarder que si encore valide
                        if (entry.getValue() > now) {
                            stmt.setString(1, entry.getKey().toString());
                            stmt.setLong(2, entry.getValue());
                            stmt.addBatch();
                        }
                    }
                    stmt.executeBatch();
                }
            });
            
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Police] Erreur sauvegarde tased_players", e);
        }
    }

    /**
     * Sauvegarde le spawn de prison d'une ville
     */
    public void savePrisonSpawn(String townName, Location spawnLocation) {
        try {
            String sql = "INSERT OR REPLACE INTO prisons (" +
                "town_name, spawn_world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?)";

            connectionManager.executeUpdate(sql,
                townName,
                spawnLocation.getWorld().getName(),
                spawnLocation.getX(),
                spawnLocation.getY(),
                spawnLocation.getZ(),
                spawnLocation.getYaw(),
                spawnLocation.getPitch()
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Prison] Erreur sauvegarde spawn prison", e);
        }
    }

    /**
     * Charge le spawn de prison d'une ville
     */
    public Location loadPrisonSpawn(String townName) {
        try (Connection conn = connectionManager.getRawConnection()) {
            String sql = "SELECT * FROM prisons WHERE town_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, townName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String worldName = rs.getString("spawn_world");
                        return new Location(
                            Bukkit.getWorld(worldName),
                            rs.getDouble("spawn_x"),
                            rs.getDouble("spawn_y"),
                            rs.getDouble("spawn_z"),
                            (float) rs.getDouble("spawn_yaw"),
                            (float) rs.getDouble("spawn_pitch")
                        );
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Prison] Erreur chargement spawn prison", e);
        }

        return null;
    }
}