package com.gravityyfh.roleplaycity.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Service de persistance SQLite pour le mode service
 * Remplace le syst\u00e8me YAML de ServiceModeManager
 */
public class ServicePersistenceService {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public ServicePersistenceService(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Charge les sessions actives
     */
    public Map<UUID, ServiceModeData> loadActiveSessions() {
        Map<UUID, ServiceModeData> sessions = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection()) {
            String sql = "SELECT * FROM service_sessions WHERE is_active = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
                    String playerName = rs.getString("player_name");
                    String entrepriseSiret = rs.getString("entreprise_siret");
                    LocalDateTime startTime = LocalDateTime.parse(rs.getString("start_time"), DATE_FORMAT);
                    double totalEarned = rs.getDouble("total_earned");

                    ServiceModeData data = new ServiceModeData(playerUuid, playerName);
                    data.setActiveEnterprise(entrepriseSiret);
                    data.setStartTime(startTime);
                    data.setTotalEarned(totalEarned);

                    sessions.put(playerUuid, data);
                }
            }

            plugin.getLogger().info("[Service] Charg\u00e9 " + sessions.size() + " sessions actives depuis SQLite");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Service] Erreur chargement sessions", e);
        }

        return sessions;
    }

    /**
     * Sauvegarde une session
     */
    public void saveSession(ServiceModeData data) {
        // Guard: vérifier que les données essentielles sont présentes
        if (data == null || data.getPlayerUUID() == null) {
            plugin.getLogger().warning("[Service] Tentative de sauvegarde avec données null");
            return;
        }

        // Guard: vérifier que startTime est défini
        LocalDateTime startTime = data.getStartTime();
        if (startTime == null) {
            plugin.getLogger().warning("[Service] Tentative de sauvegarde sans startTime pour " + data.getPlayerName());
            return;
        }

        // Guard: vérifier que l'entreprise est définie
        String entrepriseSiret = data.getActiveEnterprise();
        if (entrepriseSiret == null || entrepriseSiret.isEmpty()) {
            plugin.getLogger().warning("[Service] Tentative de sauvegarde sans entreprise pour " + data.getPlayerName());
            return;
        }

        // Guard: vérifier que l'entreprise existe dans la base de données (FOREIGN KEY)
        if (!entrepriseExists(entrepriseSiret)) {
            plugin.getLogger().warning("[Service] Entreprise " + entrepriseSiret + " n'existe pas en base - session non sauvegardée pour " + data.getPlayerName());
            return;
        }

        try {
            String playerUuid = data.getPlayerUUID().toString();

            // Vérifier si une session active existe déjà pour ce joueur
            if (data.isActive()) {
                // Mettre à jour la session active existante ou en créer une nouvelle
                int updated = connectionManager.executeUpdate(
                    "UPDATE service_sessions SET " +
                    "player_name = ?, entreprise_siret = ?, start_time = ?, " +
                    "total_earned = ? WHERE player_uuid = ? AND is_active = 1",
                    data.getPlayerName(),
                    entrepriseSiret,
                    startTime.format(DATE_FORMAT),
                    data.getTotalEarned(),
                    playerUuid
                );

                if (updated == 0) {
                    // Aucune session active trouvée, en créer une nouvelle
                    connectionManager.executeUpdate(
                        "INSERT INTO service_sessions (" +
                        "player_uuid, player_name, entreprise_siret, start_time, " +
                        "end_time, total_earned, is_active" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                        playerUuid,
                        data.getPlayerName(),
                        entrepriseSiret,
                        startTime.format(DATE_FORMAT),
                        null,
                        data.getTotalEarned(),
                        1
                    );
                }
            } else {
                // Session terminée - créer un enregistrement historique
                connectionManager.executeUpdate(
                    "INSERT INTO service_sessions (" +
                    "player_uuid, player_name, entreprise_siret, start_time, " +
                    "end_time, total_earned, is_active" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?)",
                    playerUuid,
                    data.getPlayerName(),
                    entrepriseSiret,
                    startTime.format(DATE_FORMAT),
                    data.getEndTime() != null ? data.getEndTime().format(DATE_FORMAT) : null,
                    data.getTotalEarned(),
                    0
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Service] Erreur sauvegarde session pour " + data.getPlayerName(), e);
        }
    }

    /**
     * Vérifie si une entreprise existe dans la base de données
     */
    private boolean entrepriseExists(String siret) {
        if (siret == null || siret.isEmpty()) {
            return false;
        }
        try (Connection conn = connectionManager.getRawConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM entreprises WHERE siret = ?")) {
            stmt.setString(1, siret);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "[Service] Erreur vérification entreprise " + siret, e);
            return false;
        }
    }

    /**
     * Termine une session
     */
    public void endSession(UUID playerUuid, LocalDateTime endTime, double totalEarned) {
        try {
            connectionManager.executeUpdate(
                "UPDATE service_sessions SET end_time = ?, total_earned = ?, is_active = 0 " +
                "WHERE player_uuid = ? AND is_active = 1",
                endTime.format(DATE_FORMAT),
                totalEarned,
                playerUuid.toString()
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Service] Erreur fin session", e);
        }
    }
}
