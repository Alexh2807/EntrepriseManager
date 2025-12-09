package com.gravityyfh.roleplaycity.shop.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.shop.model.Shop;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Service de persistance SQLite pour les boutiques
 * Remplace ShopPersistence (YAML)
 */
public class ShopPersistenceService {
    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public ShopPersistenceService(RoleplayCity plugin, ConnectionManager connectionManager) {
        this.plugin = plugin;
        this.connectionManager = connectionManager;
    }

    /**
     * Charge toutes les boutiques
     */
    public Map<String, Shop> loadShops() {
        Map<String, Shop> shops = new HashMap<>();

        try (Connection conn = connectionManager.getRawConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM shops")) {

            while (rs.next()) {
                try {
                    Shop shop = loadShop(conn, rs);
                    if (shop != null) {
                        shops.put(shop.getShopId().toString(), shop);
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[Shops] Erreur chargement boutique ID: " + rs.getString("shop_uuid"), e);
                }
            }

            plugin.getLogger().info("[Shops] Chargé " + shops.size() + " boutiques depuis SQLite");

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "[Shops] Erreur chargement boutiques", e);
        }

        return shops;
    }

    private Shop loadShop(Connection conn, ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();

        // Identifiants
        String shopUuid = rs.getString("shop_uuid");
        map.put("shopId", shopUuid);
        map.put("entrepriseSiret", rs.getString("entreprise_siret"));
        map.put("entrepriseName", rs.getString("entreprise_name"));
        map.put("ownerUUID", rs.getString("owner_uuid"));
        map.put("ownerName", rs.getString("owner_name"));

        // Locations
        map.put("chest.world", rs.getString("chest_world"));
        map.put("chest.x", rs.getDouble("chest_x"));
        map.put("chest.y", rs.getDouble("chest_y"));
        map.put("chest.z", rs.getDouble("chest_z"));

        map.put("sign.world", rs.getString("sign_world"));
        map.put("sign.x", rs.getDouble("sign_x"));
        map.put("sign.y", rs.getDouble("sign_y"));
        map.put("sign.z", rs.getDouble("sign_z"));

        if (rs.getString("hologram_world") != null) {
            map.put("hologram.world", rs.getString("hologram_world"));
            map.put("hologram.x", rs.getDouble("hologram_x"));
            map.put("hologram.y", rs.getDouble("hologram_y"));
            map.put("hologram.z", rs.getDouble("hologram_z"));
        }

        // Data
        map.put("itemTemplate", rs.getString("item_template"));
        map.put("quantityPerSale", rs.getInt("quantity_per_sale"));
        map.put("pricePerSale", rs.getDouble("price_per_sale"));

        // Stats
        map.put("creationDate", rs.getString("creation_date"));
        if (rs.getString("last_activity") != null) map.put("lastActivity", rs.getString("last_activity"));
        if (rs.getString("last_stock_check") != null) map.put("lastStockCheck", rs.getString("last_stock_check"));
        if (rs.getString("last_purchase") != null) map.put("lastPurchase", rs.getString("last_purchase"));
        
        map.put("totalSales", rs.getInt("total_sales"));
        // Gérer le cas où la colonne n'existe pas encore (anciennes bases)
        map.put("totalItemsSold", getIntColumnSafe(rs, "total_items_sold", 0));
        map.put("totalRevenue", rs.getDouble("total_revenue"));

        map.put("cachedStock", rs.getInt("cached_stock"));

        // Status & Entities
        map.put("status", rs.getString("status"));
        if (rs.getString("display_item_entity_id") != null) {
            map.put("displayItemEntityId", rs.getString("display_item_entity_id"));
        }
        
        String holoIds = rs.getString("hologram_entity_ids");
        if (holoIds != null && !holoIds.isEmpty()) {
            List<String> ids = new ArrayList<>(Arrays.asList(holoIds.split(",")));
            map.put("hologramTextEntityIds", ids);
        }

        // Top Buyers
        Map<String, Object> topBuyers = loadTopBuyers(conn, shopUuid);
        if (!topBuyers.isEmpty()) {
            map.put("topBuyers", topBuyers);
        }

        return Shop.deserialize(map);
    }

    private Map<String, Object> loadTopBuyers(Connection conn, String shopUuid) throws SQLException {
        Map<String, Object> buyers = new HashMap<>();
        String sql = "SELECT buyer_name, count FROM shop_top_buyers WHERE shop_uuid = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, shopUuid);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    buyers.put(rs.getString("buyer_name"), rs.getInt("count"));
                }
            }
        }
        return buyers;
    }

    /**
     * Sauvegarde toutes les boutiques
     */
    public CompletableFuture<Void> saveShops(Collection<Shop> shops) {
        return CompletableFuture.runAsync(() -> {
            try {
                connectionManager.executeTransaction(conn -> {
                    for (Shop shop : shops) {
                        saveShop(conn, shop);
                    }
                });
                plugin.getLogger().info("[Shops] Sauvegardé " + shops.size() + " boutiques");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[Shops] Erreur sauvegarde", e);
            }
        });
    }

    private void saveShop(Connection conn, Shop shop) throws SQLException {
        Map<String, Object> map = shop.serialize();
        
        String sql = """
            INSERT INTO shops (
                shop_uuid, entreprise_siret, entreprise_name, owner_uuid, owner_name,
                chest_world, chest_x, chest_y, chest_z,
                sign_world, sign_x, sign_y, sign_z,
                hologram_world, hologram_x, hologram_y, hologram_z,
                item_template, quantity_per_sale, price_per_sale,
                creation_date, last_activity, last_stock_check, last_purchase,
                total_sales, total_items_sold, total_revenue, cached_stock,
                status, display_item_entity_id, hologram_entity_ids
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(shop_uuid) DO UPDATE SET
                entreprise_siret = excluded.entreprise_siret,
                entreprise_name = excluded.entreprise_name,
                item_template = excluded.item_template,
                quantity_per_sale = excluded.quantity_per_sale,
                price_per_sale = excluded.price_per_sale,
                last_activity = excluded.last_activity,
                last_stock_check = excluded.last_stock_check,
                last_purchase = excluded.last_purchase,
                total_sales = excluded.total_sales,
                total_items_sold = excluded.total_items_sold,
                total_revenue = excluded.total_revenue,
                cached_stock = excluded.cached_stock,
                status = excluded.status,
                display_item_entity_id = excluded.display_item_entity_id,
                hologram_entity_ids = excluded.hologram_entity_ids
            """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, (String) map.get("shopId"));
            stmt.setString(2, (String) map.get("entrepriseSiret"));
            stmt.setString(3, (String) map.getOrDefault("entrepriseName", "Unknown"));
            stmt.setString(4, (String) map.get("ownerUUID"));
            stmt.setString(5, (String) map.getOrDefault("ownerName", "Unknown"));

            // Chest Loc
            stmt.setString(6, (String) map.get("chest.world"));
            stmt.setDouble(7, (Double) map.get("chest.x"));
            stmt.setDouble(8, (Double) map.get("chest.y"));
            stmt.setDouble(9, (Double) map.get("chest.z"));

            // Sign Loc
            stmt.setString(10, (String) map.get("sign.world"));
            stmt.setDouble(11, (Double) map.get("sign.x"));
            stmt.setDouble(12, (Double) map.get("sign.y"));
            stmt.setDouble(13, (Double) map.get("sign.z"));

            // Hologram Loc
            if (map.containsKey("hologram.world")) {
                stmt.setString(14, (String) map.get("hologram.world"));
                stmt.setDouble(15, (Double) map.get("hologram.x"));
                stmt.setDouble(16, (Double) map.get("hologram.y"));
                stmt.setDouble(17, (Double) map.get("hologram.z"));
            } else {
                stmt.setNull(14, Types.VARCHAR);
                stmt.setNull(15, Types.DOUBLE);
                stmt.setNull(16, Types.DOUBLE);
                stmt.setNull(17, Types.DOUBLE);
            }

            // Data
            stmt.setString(18, (String) map.get("itemTemplate"));
            stmt.setInt(19, (Integer) map.getOrDefault("quantityPerSale", 1));
            stmt.setDouble(20, (Double) map.get("pricePerSale"));

            // Stats
            stmt.setString(21, (String) map.get("creationDate"));
            stmt.setString(22, (String) map.get("lastActivity"));
            stmt.setString(23, (String) map.get("lastStockCheck"));
            stmt.setString(24, (String) map.get("lastPurchase"));

            stmt.setInt(25, (Integer) map.getOrDefault("totalSales", 0));
            stmt.setInt(26, (Integer) map.getOrDefault("totalItemsSold", 0));
            stmt.setDouble(27, (Double) map.getOrDefault("totalRevenue", 0.0));
            stmt.setInt(28, (Integer) map.getOrDefault("cachedStock", 0));

            // Status
            stmt.setString(29, (String) map.getOrDefault("status", "ACTIVE"));
            stmt.setString(30, (String) map.get("displayItemEntityId"));

            @SuppressWarnings("unchecked")
            List<String> holoIds = (List<String>) map.get("hologramTextEntityIds");
            if (holoIds != null && !holoIds.isEmpty()) {
                stmt.setString(31, String.join(",", holoIds));
            } else {
                stmt.setNull(31, Types.VARCHAR);
            }

            stmt.executeUpdate();

            // Sauvegarder aussi dans la table shop_stock pour audit
            saveShopStockAudit(conn, (String) map.get("shopId"), (String) map.get("itemTemplate"), (Integer) map.getOrDefault("cachedStock", 0));
        }
        
        // Save Top Buyers
        saveTopBuyers(conn, (String) map.get("shopId"), (Map<String, Integer>) map.get("topBuyers"));
    }
    
    /**
     * Sauvegarde l'audit de stock d'un shop
     * Note: Cette fonction est optionnelle et ne bloque pas si la table n'existe pas correctement
     */
    private void saveShopStockAudit(Connection conn, String shopId, String itemTemplate, int quantity) {
        // Désactivé temporairement - la table shop_stock a un schéma différent
        // et cette fonctionnalité d'audit n'est pas essentielle
        // Le stock est déjà sauvegardé dans cached_stock de la table shops
    }

    private void saveTopBuyers(Connection conn, String shopUuid, Map<String, Integer> topBuyers) throws SQLException {
        // Delete old
        String deleteSql = "DELETE FROM shop_top_buyers WHERE shop_uuid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setString(1, shopUuid);
            stmt.executeUpdate();
        }

        if (topBuyers == null || topBuyers.isEmpty()) return;

        String insertSql = "INSERT INTO shop_top_buyers (shop_uuid, buyer_name, count) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
            for (Map.Entry<String, Integer> entry : topBuyers.entrySet()) {
                stmt.setString(1, shopUuid);
                stmt.setString(2, entry.getKey());
                stmt.setInt(3, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Récupère une colonne INT de manière sécurisée (retourne defaultValue si la colonne n'existe pas)
     */
    private int getIntColumnSafe(ResultSet rs, String columnName, int defaultValue) {
        try {
            // Vérifier si la colonne existe dans le ResultSet
            rs.findColumn(columnName);
            return rs.getInt(columnName);
        } catch (SQLException e) {
            // La colonne n'existe pas, retourner la valeur par défaut
            return defaultValue;
        }
    }

    /**
     * Supprime une boutique
     */
    public CompletableFuture<Void> deleteShop(UUID shopId) {
        return CompletableFuture.runAsync(() -> {
            try {
                connectionManager.executeUpdate("DELETE FROM shops WHERE shop_uuid = ?", shopId.toString());
                plugin.getLogger().info("[Shops] Boutique supprimée: " + shopId);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "[Shops] Erreur suppression", e);
            }
        });
    }
}