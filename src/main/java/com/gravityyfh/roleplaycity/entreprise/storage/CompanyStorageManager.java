package com.gravityyfh.roleplaycity.entreprise.storage;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.database.ConnectionManager;
import com.gravityyfh.roleplaycity.util.InventorySerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Gestionnaire des coffres virtuels d'entreprise.
 * Stocke les items récoltés par les employés en mode service.
 * Version SQLite.
 */
public class CompanyStorageManager {

    private final RoleplayCity plugin;
    private final ConnectionManager connectionManager;
    private final Map<String, Inventory> loadedInventories = new HashMap<>();

    public CompanyStorageManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.connectionManager = plugin.getConnectionManager();
    }

    /**
     * Ouvre le coffre virtuel d'une entreprise pour un joueur (Gérant)
     */
    public void openStorage(Player player, String companyName) {
        // Charger de manière asynchrone pour ne pas freeze si pas en cache
        if (!loadedInventories.containsKey(companyName)) {
            connectionManager.executeAsync(() -> {
                Inventory inv = loadInventoryFromDB(companyName);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    loadedInventories.put(companyName, inv);
                    player.openInventory(inv);
                });
            });
        } else {
            player.openInventory(loadedInventories.get(companyName));
        }
    }

    /**
     * Ajoute des items au coffre d'une entreprise (automatique via Mode Service)
     * @return true si tout a été ajouté, false si le coffre est plein (reste au sol)
     */
    public boolean addItemToStorage(String companyName, ItemStack item) {
        if (item == null || item.getType().isAir()) return true;

        // Ici on doit charger de manière synchrone car l'event de drop attend une réponse immédiate
        Inventory inv = getInventorySync(companyName);
        HashMap<Integer, ItemStack> leftover = inv.addItem(item);

        // Sauvegarder après modification (Async)
        saveInventory(companyName, inv);

        return leftover.isEmpty();
    }

    /**
     * Récupère l'inventaire (Cache ou DB Synchrone)
     */
    private Inventory getInventorySync(String companyName) {
        if (loadedInventories.containsKey(companyName)) {
            return loadedInventories.get(companyName);
        }
        Inventory inv = loadInventoryFromDB(companyName);
        loadedInventories.put(companyName, inv);
        return inv;
    }

    /**
     * Charge l'inventaire depuis la DB
     */
    private Inventory loadInventoryFromDB(String companyName) {
        String sql = "SELECT content FROM company_storage WHERE company_name = ?";
        
        try (Connection conn = connectionManager.getRawConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, companyName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String content = rs.getString("content");
                    if (content != null && !content.isEmpty()) {
                        try {
                            return InventorySerializer.fromBase64(content, "Coffre: " + companyName);
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.SEVERE, "Erreur désérialisation coffre: " + companyName, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur chargement coffre SQL: " + companyName, e);
        }

        // Retourne un inventaire vide si introuvable ou erreur
        return Bukkit.createInventory(null, 54, "Coffre: " + companyName);
    }

    /**
     * Sauvegarde l'inventaire dans la DB (Asynchrone)
     */
    public void saveInventory(String companyName, Inventory inv) {
        // Sérialiser sur le thread principal pour éviter les accès concurrents à l'inventaire Bukkit
        String base64 = InventorySerializer.toBase64(inv);
        long now = System.currentTimeMillis();

        connectionManager.executeAsync(() -> {
            String sql = "INSERT OR REPLACE INTO company_storage (company_name, content, updated_at) VALUES (?, ?, ?)";
            try (Connection conn = connectionManager.getRawConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, companyName);
                stmt.setString(2, base64);
                stmt.setLong(3, now);
                stmt.executeUpdate();
                
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur sauvegarde coffre: " + companyName, e);
            }
        });
    }

    /**
     * Sauvegarde globale (appelée au disable) - Synchrone pour être sûr
     */
    public void saveAll() {
        String sql = "INSERT OR REPLACE INTO company_storage (company_name, content, updated_at) VALUES (?, ?, ?)";
        long now = System.currentTimeMillis();

        try (Connection conn = connectionManager.getRawConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (Map.Entry<String, Inventory> entry : loadedInventories.entrySet()) {
                String base64 = InventorySerializer.toBase64(entry.getValue());
                
                stmt.setString(1, entry.getKey());
                stmt.setString(2, base64);
                stmt.setLong(3, now);
                stmt.addBatch();
            }
            stmt.executeBatch();
            plugin.getLogger().info("Sauvegarde de " + loadedInventories.size() + " coffres d'entreprise.");
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur sauvegarde globale coffres", e);
        }
    }
}
