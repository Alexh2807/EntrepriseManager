package com.gravityyfh.roleplaycity.backpack.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.backpack.model.BackpackData;
import com.gravityyfh.roleplaycity.backpack.model.BackpackType;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

/**
 * Service de persistance SQLite pour les sacs à dos
 */
public class BackpackPersistenceService {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public BackpackPersistenceService(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Charge un backpack
     */
    public BackpackData loadBackpack(UUID backpackId) {
        try (Connection conn = connectionManager.getRawConnection()) {
            String sql = "SELECT * FROM backpacks WHERE backpack_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, backpackId.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String typeStr = rs.getString("backpack_type");
                        
                        if (plugin.getBackpackItemManager() == null) return null;
                        
                        BackpackType type = plugin.getBackpackItemManager().getType(typeStr);
                        if (type == null) {
                            plugin.getLogger().warning("Type de backpack inconnu: " + typeStr);
                            return null;
                        }
                        
                        int size = rs.getInt("size");
                        String serializedData = rs.getString("backpack_data");

                        ItemStack[] contents = BackpackSerializer.deserializeData(serializedData, size);

                        return new BackpackData(backpackId, contents, System.currentTimeMillis(), size);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Backpack] Erreur chargement backpack " + backpackId, e);
        }

        return null;
    }

    /**
     * Sauvegarde un backpack
     */
    public void saveBackpack(UUID backpackId, UUID ownerUuid, BackpackType type,
                             ItemStack[] contents) {
        try {
            String serialized = BackpackSerializer.serialize(contents);

            String sql = "INSERT OR REPLACE INTO backpacks (" +
                "backpack_id, owner_uuid, backpack_type, backpack_data, last_update, size" +
                ") VALUES (?, ?, ?, ?, ?, ?)";

            connectionManager.executeUpdate(sql,
                backpackId.toString(),
                ownerUuid != null ? ownerUuid.toString() : null,
                type.getId(),
                serialized,
                LocalDateTime.now().format(DATE_FORMAT),
                type.getSize()
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Backpack] Erreur sauvegarde backpack " + backpackId, e);
        }
    }

    /**
     * Supprime un backpack
     */
    public void deleteBackpack(UUID backpackId) {
        try {
            connectionManager.executeUpdate("DELETE FROM backpacks WHERE backpack_id = ?",
                backpackId.toString());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Backpack] Erreur suppression backpack", e);
        }
    }

    /**
     * Charge tous les backpacks d'un joueur
     */
    public List<BackpackData> loadPlayerBackpacks(UUID ownerUuid) {
        List<BackpackData> backpacks = new ArrayList<>();

        try (Connection conn = connectionManager.getRawConnection()) {
            String sql = "SELECT * FROM backpacks WHERE owner_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ownerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UUID backpackId = UUID.fromString(rs.getString("backpack_id"));
                        String typeStr = rs.getString("backpack_type");
                        
                        if (plugin.getBackpackItemManager() == null) continue;
                        
                        BackpackType type = plugin.getBackpackItemManager().getType(typeStr);
                        if (type == null) continue;
                        
                        int size = rs.getInt("size");
                        String serializedData = rs.getString("backpack_data");

                        ItemStack[] contents = BackpackSerializer.deserializeData(serializedData, size);
                        
                        backpacks.add(new BackpackData(backpackId, contents, System.currentTimeMillis(), size));
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Backpack] Erreur chargement backpacks joueur", e);
        }

        return backpacks;
    }
}