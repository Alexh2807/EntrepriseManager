package com.gravityyfh.roleplaycity.town.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Fine.FineStatus;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Service de persistance SQLite pour les amendes
 */
public class FinesPersistenceService {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public FinesPersistenceService(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Charge toutes les amendes
     */
    public Map<String, List<Fine>> loadFines() {
        Map<String, List<Fine>> finesByTown = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM fines ORDER BY issue_date DESC")) {

            while (rs.next()) {
                try {
                    Fine fine = loadFine(rs);
                    String townName = rs.getString("town_name");

                    finesByTown.computeIfAbsent(townName, k -> new ArrayList<>()).add(fine);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Fines] Erreur chargement amende", e);
                }
            }

            int totalFines = finesByTown.values().stream().mapToInt(List::size).sum();
            plugin.getLogger().info("[Fines] Chargé " + totalFines + " amendes depuis SQLite");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fines] Erreur chargement amendes", e);
        }

        return finesByTown;
    }

    private Fine loadFine(ResultSet rs) throws SQLException {
        UUID fineId = UUID.fromString(rs.getString("fine_id"));
        String townName = rs.getString("town_name");
        UUID offenderUuid = UUID.fromString(rs.getString("offender_uuid"));
        String offenderName = rs.getString("offender_name");
        UUID policierUuid = UUID.fromString(rs.getString("policier_uuid"));
        String policierName = rs.getString("policier_name");
        String reason = rs.getString("reason");
        double amount = rs.getDouble("amount");
        LocalDateTime issueDate = LocalDateTime.parse(rs.getString("issue_date"), DATE_FORMAT);
        FineStatus status = FineStatus.valueOf(rs.getString("status"));

        Fine fine = new Fine(fineId, townName, offenderUuid, offenderName, 
            policierUuid, policierName, reason, amount, issueDate, status);

        // Dates optionnelles
        String paidDateStr = rs.getString("paid_date");
        if (paidDateStr != null) {
            fine.setPaidDate(LocalDateTime.parse(paidDateStr, DATE_FORMAT));
        }

        String contestedDateStr = rs.getString("contested_date");
        if (contestedDateStr != null) {
            fine.setContestedDate(LocalDateTime.parse(contestedDateStr, DATE_FORMAT));
            fine.setContestReason(rs.getString("contest_reason"));
        }

        // Jugement
        String judgeUuidStr = rs.getString("judge_uuid");
        if (judgeUuidStr != null) {
            fine.setJudgeUuid(UUID.fromString(judgeUuidStr));
            fine.setJudgeVerdict(rs.getString("judge_verdict"));
            String judgeDateStr = rs.getString("judge_date");
            if (judgeDateStr != null) {
                fine.setJudgeDate(LocalDateTime.parse(judgeDateStr, DATE_FORMAT));
            }
        }

        return fine;
    }

    /**
     * Sauvegarde toutes les amendes
     */
    public void saveFines(Map<String, List<Fine>> finesByTown) {
        try {
            connectionManager.executeTransaction(conn -> {
                // Supprimer toutes les amendes
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("DELETE FROM fines");
                }

                // Insérer toutes les amendes
                String sql = "INSERT INTO fines (" +
                    "fine_id, town_name, offender_uuid, offender_name, " +
                    "policier_uuid, policier_name, reason, amount, issue_date, " +
                    "status, paid_date, contested_date, contest_reason, " +
                    "judge_uuid, judge_verdict, judge_date" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    for (Map.Entry<String, List<Fine>> entry : finesByTown.entrySet()) {
                        String townName = entry.getKey();
                        for (Fine fine : entry.getValue()) {
                            stmt.setString(1, fine.getFineId().toString());
                            stmt.setString(2, townName);
                            stmt.setString(3, fine.getOffenderUuid().toString());
                            stmt.setString(4, fine.getOffenderName());
                            stmt.setString(5, fine.getPolicierUuid().toString());
                            stmt.setString(6, fine.getPolicierName());
                            stmt.setString(7, fine.getReason());
                            stmt.setDouble(8, fine.getAmount());
                            stmt.setString(9, fine.getIssueDate().format(DATE_FORMAT));
                            stmt.setString(10, fine.getStatus().name());

                            // Optionnels
                            if (fine.getPaidDate() != null) {
                                stmt.setString(11, fine.getPaidDate().format(DATE_FORMAT));
                            } else {
                                stmt.setNull(11, Types.VARCHAR);
                            }

                            if (fine.getContestedDate() != null) {
                                stmt.setString(12, fine.getContestedDate().format(DATE_FORMAT));
                                stmt.setString(13, fine.getContestReason());
                            } else {
                                stmt.setNull(12, Types.VARCHAR);
                                stmt.setNull(13, Types.VARCHAR);
                            }

                            if (fine.getJudgeUuid() != null) {
                                stmt.setString(14, fine.getJudgeUuid().toString());
                                stmt.setString(15, fine.getJudgeVerdict());
                                // Guard contre NPE sur judgeDate
                                if (fine.getJudgeDate() != null) {
                                    stmt.setString(16, fine.getJudgeDate().format(DATE_FORMAT));
                                } else {
                                    stmt.setNull(16, Types.VARCHAR);
                                }
                            } else {
                                stmt.setNull(14, Types.VARCHAR);
                                stmt.setNull(15, Types.VARCHAR);
                                stmt.setNull(16, Types.VARCHAR);
                            }

                            stmt.addBatch();
                        }
                    }
                    stmt.executeBatch();
                }
            });

            int totalFines = finesByTown.values().stream().mapToInt(List::size).sum();
            plugin.getLogger().info("[Fines] Sauvegardé " + totalFines + " amendes");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[Fines] Erreur sauvegarde amendes", e);
        }
    }

    /**
     * Sauvegarde une amende individuelle
     */
    public void saveFine(String townName, Fine fine) {
        try {
            String sql = "INSERT OR REPLACE INTO fines (" +
                "fine_id, town_name, offender_uuid, offender_name, " +
                "policier_uuid, policier_name, reason, amount, issue_date, status" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            connectionManager.executeUpdate(sql,
                fine.getFineId().toString(),
                townName,
                fine.getOffenderUuid().toString(),
                fine.getOffenderName(),
                fine.getPolicierUuid().toString(),
                fine.getPolicierName(),
                fine.getReason(),
                fine.getAmount(),
                fine.getIssueDate().format(DATE_FORMAT),
                fine.getStatus().name()
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Fines] Erreur sauvegarde amende", e);
        }
    }
}