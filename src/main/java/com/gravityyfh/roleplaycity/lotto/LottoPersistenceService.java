package com.gravityyfh.roleplaycity.lotto;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gère la persistence des données du Loto en base de données.
 */
public class LottoPersistenceService {

    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;

    public LottoPersistenceService(RoleplayCity plugin) {
        this.plugin = plugin;
        this.connectionManager = plugin.getConnectionManager();
    }

    /**
     * Sauvegarde l'état actuel du loto.
     */
    public void saveState(LottoManager.LottoState state) {
        try {
            connectionManager.executeUpdate("INSERT OR REPLACE INTO lotto_data (data_key, data_value) VALUES (?, ?)",
                "state", state.name());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Loto] Erreur sauvegarde état", e);
        }
    }

    /**
     * Charge l'état du loto.
     */
    public LottoManager.LottoState loadState() {
        try (Connection conn = connectionManager.getRawConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT data_value FROM lotto_data WHERE data_key = ?")) {
            
            stmt.setString(1, "state");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return LottoManager.LottoState.valueOf(rs.getString("data_value"));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Loto] Erreur chargement état (défaut: WAITING)", e);
        }
        return LottoManager.LottoState.WAITING;
    }

    /**
     * Ajoute un ticket à la base de données.
     */
    public void addTicket(UUID playerUuid) {
        try {
            connectionManager.executeUpdate("INSERT INTO lotto_tickets (player_uuid) VALUES (?)", playerUuid.toString());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Loto] Erreur sauvegarde ticket", e);
        }
    }

    /**
     * Charge tous les tickets.
     */
    public List<UUID> loadTickets() {
        List<UUID> tickets = new ArrayList<>();
        try (Connection conn = connectionManager.getRawConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT player_uuid FROM lotto_tickets")) {
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    tickets.add(UUID.fromString(rs.getString("player_uuid")));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Loto] Erreur chargement tickets", e);
        }
        return tickets;
    }

    /**
     * Vide tous les tickets (après tirage ou reset).
     */
    public void clearTickets() {
        try {
            connectionManager.executeUpdate("DELETE FROM lotto_tickets");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Loto] Erreur suppression tickets", e);
        }
    }
}
