package com.gravityyfh.roleplaycity.mdt.inventory;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gestionnaire de sauvegarde et restauration des inventaires pour le MDT Rush
 */
public class InventoryBackupManager {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public InventoryBackupManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.connectionManager = plugin.getConnectionManager();
    }

    /**
     * Sauvegarde l'état complet d'un joueur (inventaire, armure, XP, vie, etc.)
     */
    public void savePlayerState(Player player) {
        try {
            PlayerInventory inv = player.getInventory();

            // Sérialiser l'inventaire principal (slots 0-35)
            String inventoryData = serializeContents(inv.getContents());

            // Sérialiser l'armure
            String armorData = serializeContents(inv.getArmorContents());

            // Sérialiser l'offhand
            String offhandData = serializeItem(inv.getItemInOffHand());

            // Récupérer les autres données
            int expLevel = player.getLevel();
            float expProgress = player.getExp();
            double health = player.getHealth();
            int foodLevel = player.getFoodLevel();
            String gamemode = player.getGameMode().name();

            // Sauvegarder en base
            String sql = """
                INSERT OR REPLACE INTO mdt_inventory_backup
                (player_uuid, player_name, inventory_data, armor_data, offhand_data,
                 exp_level, exp_progress, health, food_level, gamemode, saved_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

            connectionManager.executeUpdate(sql,
                    player.getUniqueId().toString(),
                    player.getName(),
                    inventoryData,
                    armorData,
                    offhandData,
                    expLevel,
                    expProgress,
                    health,
                    foodLevel,
                    gamemode,
                    LocalDateTime.now().format(DATE_FORMAT)
            );

            plugin.getLogger().info("[MDT] Inventaire sauvegardé pour " + player.getName());

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MDT] Erreur lors de la sauvegarde de l'inventaire de " + player.getName(), e);
        }
    }

    /**
     * Restaure l'état complet d'un joueur
     */
    public boolean restorePlayerState(Player player) {
        try {
            String sql = "SELECT * FROM mdt_inventory_backup WHERE player_uuid = ?";

            try (Connection conn = connectionManager.getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, player.getUniqueId().toString());
                ResultSet rs = stmt.executeQuery();

                if (!rs.next()) {
                    plugin.getLogger().warning("[MDT] Aucun backup trouvé pour " + player.getName());
                    return false;
                }

                // Récupérer les données
                String inventoryData = rs.getString("inventory_data");
                String armorData = rs.getString("armor_data");
                String offhandData = rs.getString("offhand_data");
                int expLevel = rs.getInt("exp_level");
                float expProgress = rs.getFloat("exp_progress");
                double health = rs.getDouble("health");
                int foodLevel = rs.getInt("food_level");
                String gamemode = rs.getString("gamemode");

                // Appliquer au joueur (sur le thread principal)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        PlayerInventory inv = player.getInventory();

                        // Vider l'inventaire actuel
                        inv.clear();

                        // Restaurer l'inventaire principal
                        ItemStack[] contents = deserializeContents(inventoryData);
                        if (contents != null) {
                            inv.setContents(contents);
                        }

                        // Restaurer l'armure
                        ItemStack[] armor = deserializeContents(armorData);
                        if (armor != null) {
                            inv.setArmorContents(armor);
                        }

                        // Restaurer l'offhand
                        ItemStack offhand = deserializeItem(offhandData);
                        if (offhand != null) {
                            inv.setItemInOffHand(offhand);
                        }

                        // Restaurer l'XP
                        player.setLevel(expLevel);
                        player.setExp(expProgress);

                        // Restaurer la vie et la nourriture
                        player.setMaxHealth(20.0); // Reset à la normale d'abord
                        player.setHealth(Math.min(health, 20.0));
                        player.setFoodLevel(foodLevel);

                        // Restaurer le gamemode
                        try {
                            player.setGameMode(GameMode.valueOf(gamemode));
                        } catch (Exception e) {
                            player.setGameMode(GameMode.SURVIVAL);
                        }

                        plugin.getLogger().info("[MDT] Inventaire restauré pour " + player.getName());

                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "[MDT] Erreur lors de la restauration de l'inventaire de " + player.getName(), e);
                    }
                });

                // Supprimer le backup après restauration
                deleteBackup(player.getUniqueId());
                return true;

            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MDT] Erreur lors de la restauration de l'inventaire de " + player.getName(), e);
            return false;
        }
    }

    /**
     * Vérifie si un joueur a un backup en attente
     */
    public boolean hasBackup(UUID playerUuid) {
        try {
            String sql = "SELECT 1 FROM mdt_inventory_backup WHERE player_uuid = ?";

            try (Connection conn = connectionManager.getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                return rs.next();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MDT] Erreur lors de la vérification du backup", e);
            return false;
        }
    }

    /**
     * Supprime un backup
     */
    public void deleteBackup(UUID playerUuid) {
        String sql = "DELETE FROM mdt_inventory_backup WHERE player_uuid = ?";
        try {
            connectionManager.executeUpdate(sql, playerUuid.toString());
        } catch (java.sql.SQLException e) {
            plugin.getLogger().warning("[MDT] Erreur lors de la suppression du backup: " + e.getMessage());
        }
    }

    /**
     * Restaure tous les backups en attente (appelé au démarrage du serveur)
     */
    public void restoreAllPendingBackups() {
        try {
            String sql = "SELECT player_uuid FROM mdt_inventory_backup";

            try (Connection conn = connectionManager.getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    String uuidStr = rs.getString("player_uuid");
                    UUID playerUuid = UUID.fromString(uuidStr);
                    Player player = Bukkit.getPlayer(playerUuid);

                    if (player != null && player.isOnline()) {
                        restorePlayerState(player);
                        plugin.getLogger().info("[MDT] Backup restauré automatiquement pour " + player.getName());
                    }
                    // Si le joueur n'est pas en ligne, le backup sera restauré à sa connexion
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[MDT] Erreur lors de la restauration des backups en attente", e);
        }
    }

    /**
     * Vérifie et restaure l'inventaire d'un joueur qui se connecte
     */
    public void checkAndRestoreOnJoin(Player player) {
        if (hasBackup(player.getUniqueId())) {
            plugin.getLogger().info("[MDT] Backup en attente détecté pour " + player.getName() + ", restauration...");
            restorePlayerState(player);
        }
    }

    // ==================== SÉRIALISATION ====================

    private String serializeContents(ItemStack[] contents) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(contents.length);
            for (ItemStack item : contents) {
                dataOutput.writeObject(item);
            }

            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MDT] Erreur de sérialisation", e);
            return null;
        }
    }

    private ItemStack[] deserializeContents(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);

            int size = dataInput.readInt();
            ItemStack[] contents = new ItemStack[size];

            for (int i = 0; i < size; i++) {
                contents[i] = (ItemStack) dataInput.readObject();
            }

            dataInput.close();
            return contents;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MDT] Erreur de désérialisation", e);
            return null;
        }
    }

    private String serializeItem(ItemStack item) {
        if (item == null) {
            return null;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MDT] Erreur de sérialisation d'item", e);
            return null;
        }
    }

    private ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[MDT] Erreur de désérialisation d'item", e);
            return null;
        }
    }
}
