package com.gravityyfh.roleplaycity.medical.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.medical.data.InjuredPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Service de persistance SQLite pour le syst\u00e8me m\u00e9dical
 */
public class MedicalPersistenceService {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public MedicalPersistenceService(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Charge les joueurs bless\u00e9s non trait\u00e9s
     */
    public Map<UUID, InjuredPlayer> loadInjuredPlayers() {
        Map<UUID, InjuredPlayer> injured = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection()) {
            String sql = "SELECT * FROM injured_players WHERE treated = 0";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String playerName = rs.getString("player_name");
                    LocalDateTime injuryTime = LocalDateTime.parse(rs.getString("injury_time"), DATE_FORMAT);
                    String injuryType = rs.getString("injury_type");

                    Location location = null;
                    String worldName = rs.getString("location_world");
                    if (worldName != null) {
                        location = new Location(
                            Bukkit.getWorld(worldName),
                            rs.getDouble("location_x"),
                            rs.getDouble("location_y"),
                            rs.getDouble("location_z")
                        );
                    }

                    InjuredPlayer player = new InjuredPlayer(playerUuid, playerName, injuryTime, injuryType, location);
                    injured.put(playerUuid, player);
                }
            }

            plugin.getLogger().info("[Medical] Charg\u00e9 " + injured.size() + " joueurs bless\u00e9s depuis SQLite");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Medical] Erreur chargement joueurs bless\u00e9s", e);
        }

        return injured;
    }

    /**
     * Sauvegarde un joueur bless\u00e9
     */
    public void saveInjuredPlayer(InjuredPlayer player) {
        try {
            Location loc = player.getInjuryLocation();
            connectionManager.executeUpdate(
                "INSERT OR REPLACE INTO injured_players (" +
                "player_uuid, player_name, injury_time, injury_type, " +
                "location_world, location_x, location_y, location_z, treated" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                player.getPlayerUuid().toString(),
                player.getPlayerName(),
                player.getInjuryTime().format(DATE_FORMAT),
                "DEATH", // Type par d\u00e9faut
                loc != null ? loc.getWorld().getName() : null,
                loc != null ? loc.getX() : null,
                loc != null ? loc.getY() : null,
                loc != null ? loc.getZ() : null,
                0
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Medical] Erreur sauvegarde joueur bless\u00e9", e);
        }
    }

    /**
     * Marque un joueur comme trait\u00e9
     */
    public void markTreated(UUID playerUuid, UUID medicUuid, String medicName) {
        try {
            connectionManager.executeUpdate(
                "UPDATE injured_players SET treated = 1, medic_uuid = ?, medic_name = ?, treatment_time = ? " +
                "WHERE player_uuid = ?",
                medicUuid.toString(),
                medicName,
                LocalDateTime.now().format(DATE_FORMAT),
                playerUuid.toString()
            );

            plugin.getLogger().info("[Medical] Joueur trait\u00e9: " + playerUuid);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Medical] Erreur marquage trait\u00e9", e);
        }
    }

    /**
     * Enregistre un traitement
     */
    public void recordTreatment(UUID patientUuid, String patientName, UUID medicUuid,
                                 String medicName, double cost, String townName) {
        try {
            connectionManager.executeUpdate(
                "INSERT INTO medical_treatments (" +
                "patient_uuid, patient_name, medic_uuid, medic_name, " +
                "treatment_time, treatment_type, cost, town_name" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                patientUuid.toString(),
                patientName,
                medicUuid.toString(),
                medicName,
                LocalDateTime.now().format(DATE_FORMAT),
                "EMERGENCY",
                cost,
                townName
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Medical] Erreur enregistrement traitement", e);
        }
    }

    /**
     * Nettoie les joueurs blessés (après redémarrage)
     */
    public void clearAllInjured() {
        try {
            connectionManager.executeUpdate("UPDATE injured_players SET treated = 1");
            plugin.getLogger().info("[Medical] Tous les joueurs blessés ont été nettoyés");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Medical] Erreur nettoyage", e);
        }
    }

    /**
     * Supprime un joueur blessé de la base
     */
    public void deleteInjuredPlayer(UUID playerUuid) {
        try {
            connectionManager.executeUpdate("DELETE FROM injured_players WHERE player_uuid = ?", playerUuid.toString());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Medical] Erreur suppression joueur", e);
        }
    }

    /**
     * Marque que le serveur s'est arrêté proprement
     */
    public void markServerShutdown() {
        try {
            connectionManager.executeUpdate("INSERT OR REPLACE INTO metadata (key, value) VALUES ('server_shutdown_clean', 'true')");
            connectionManager.executeUpdate("INSERT OR REPLACE INTO metadata (key, value) VALUES ('shutdown_time', ?)", 
                String.valueOf(System.currentTimeMillis()));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Medical] Erreur marquage arrêt serveur", e);
        }
    }

    /**
     * Vérifie si c'était un redémarrage serveur propre
     */
    public boolean wasServerRestart() {
        try {
            return connectionManager.executeQuery("SELECT value FROM metadata WHERE key = 'server_shutdown_clean'", 
                rs -> rs.next() && "true".equals(rs.getString("value")));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Medical] Erreur lecture flag redémarrage", e);
            return false;
        }
    }

    /**
     * Nettoie le flag de redémarrage
     */
    public void clearServerRestartFlag() {
        try {
            connectionManager.executeUpdate("DELETE FROM metadata WHERE key = 'server_shutdown_clean'");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Medical] Erreur nettoyage flag", e);
        }
    }
}
