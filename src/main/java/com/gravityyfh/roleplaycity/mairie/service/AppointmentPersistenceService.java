package com.gravityyfh.roleplaycity.mairie.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.mairie.data.Appointment;
import com.gravityyfh.roleplaycity.mairie.data.AppointmentStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Service de persistance SQLite pour les rendez-vous mairie
 */
public class AppointmentPersistenceService {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public AppointmentPersistenceService(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Charge tous les rendez-vous
     */
    public Map<String, List<Appointment>> loadAppointments() {
        Map<String, List<Appointment>> appointmentsByTown = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM appointments ORDER BY request_date ASC")) {

            while (rs.next()) {
                try {
                    Appointment appointment = loadAppointment(rs);
                    String townName = rs.getString("town_name");

                    appointmentsByTown.computeIfAbsent(townName, k -> new ArrayList<>()).add(appointment);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Appointments] Erreur chargement RDV", e);
                }
            }

            int total = appointmentsByTown.values().stream().mapToInt(List::size).sum();
            plugin.getLogger().info("[Appointments] Charge " + total + " rendez-vous depuis SQLite");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Appointments] Erreur chargement rendez-vous", e);
        }

        return appointmentsByTown;
    }

    private Appointment loadAppointment(ResultSet rs) throws SQLException {
        UUID appointmentId = UUID.fromString(rs.getString("appointment_id"));
        UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
        String playerName = rs.getString("player_name");
        String townName = rs.getString("town_name");
        String subject = rs.getString("subject");
        LocalDateTime requestDate = LocalDateTime.parse(rs.getString("request_date"), DATE_FORMAT);
        AppointmentStatus status = AppointmentStatus.fromString(rs.getString("status"));

        UUID treatedByUuid = null;
        String treatedByName = null;
        LocalDateTime treatedDate = null;

        String treatedByUuidStr = rs.getString("treated_by_uuid");
        if (treatedByUuidStr != null) {
            treatedByUuid = UUID.fromString(treatedByUuidStr);
            treatedByName = rs.getString("treated_by_name");
            String treatedDateStr = rs.getString("treated_date");
            if (treatedDateStr != null) {
                treatedDate = LocalDateTime.parse(treatedDateStr, DATE_FORMAT);
            }
        }

        return new Appointment(appointmentId, playerUuid, playerName, townName,
                subject, requestDate, status, treatedByUuid, treatedByName, treatedDate);
    }

    /**
     * Sauvegarde tous les rendez-vous
     */
    public void saveAppointments(Map<String, List<Appointment>> appointmentsByTown) {
        try {
            connectionManager.executeTransaction(conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM appointments");
                }

                String sql = "INSERT INTO appointments (" +
                        "appointment_id, player_uuid, player_name, town_name, " +
                        "subject, request_date, status, treated_by_uuid, treated_by_name, treated_date" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (Map.Entry<String, List<Appointment>> entry : appointmentsByTown.entrySet()) {
                        for (Appointment appt : entry.getValue()) {
                            stmt.setString(1, appt.getAppointmentId().toString());
                            stmt.setString(2, appt.getPlayerUuid().toString());
                            stmt.setString(3, appt.getPlayerName());
                            stmt.setString(4, appt.getTownName());
                            stmt.setString(5, appt.getSubject());
                            stmt.setString(6, appt.getRequestDate().format(DATE_FORMAT));
                            stmt.setString(7, appt.getStatus().name());

                            if (appt.getTreatedByUuid() != null) {
                                stmt.setString(8, appt.getTreatedByUuid().toString());
                                stmt.setString(9, appt.getTreatedByName());
                                stmt.setString(10, appt.getTreatedDate() != null ?
                                        appt.getTreatedDate().format(DATE_FORMAT) : null);
                            } else {
                                stmt.setNull(8, Types.VARCHAR);
                                stmt.setNull(9, Types.VARCHAR);
                                stmt.setNull(10, Types.VARCHAR);
                            }

                            stmt.addBatch();
                        }
                    }
                    stmt.executeBatch();
                }
            });

            int total = appointmentsByTown.values().stream().mapToInt(List::size).sum();
            plugin.getLogger().info("[Appointments] Sauvegarde " + total + " rendez-vous");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Appointments] Erreur sauvegarde rendez-vous", e);
        }
    }

    /**
     * Sauvegarde un rendez-vous individuel (insert ou update)
     */
    public void saveAppointment(Appointment appt) {
        try {
            String sql = "INSERT OR REPLACE INTO appointments (" +
                    "appointment_id, player_uuid, player_name, town_name, " +
                    "subject, request_date, status, treated_by_uuid, treated_by_name, treated_date" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            connectionManager.executeUpdate(sql,
                    appt.getAppointmentId().toString(),
                    appt.getPlayerUuid().toString(),
                    appt.getPlayerName(),
                    appt.getTownName(),
                    appt.getSubject(),
                    appt.getRequestDate().format(DATE_FORMAT),
                    appt.getStatus().name(),
                    appt.getTreatedByUuid() != null ? appt.getTreatedByUuid().toString() : null,
                    appt.getTreatedByName(),
                    appt.getTreatedDate() != null ? appt.getTreatedDate().format(DATE_FORMAT) : null
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Appointments] Erreur sauvegarde RDV", e);
        }
    }

    /**
     * Supprime un rendez-vous
     */
    public void deleteAppointment(UUID appointmentId) {
        try {
            connectionManager.executeUpdate(
                    "DELETE FROM appointments WHERE appointment_id = ?",
                    appointmentId.toString()
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Appointments] Erreur suppression RDV", e);
        }
    }

    /**
     * Supprime les rendez-vous expirés (plus de X jours)
     */
    public int deleteExpiredAppointments(int expirationDays) {
        try {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(expirationDays);
            String sql = "DELETE FROM appointments WHERE status = 'PENDING' AND request_date < ?";

            int deleted = connectionManager.executeUpdate(sql, cutoff.format(DATE_FORMAT));
            if (deleted > 0) {
                plugin.getLogger().info("[Appointments] Supprime " + deleted + " RDV expires (+" + expirationDays + " jours)");
            }
            return deleted;

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Appointments] Erreur suppression RDV expires", e);
            return 0;
        }
    }
}
