package com.gravityyfh.entreprisemanager.Models;

import org.bukkit.Material;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class ProductionRecord {
    public final LocalDateTime timestamp;
    public final Material material;
    public final int quantity;
    public final String recordedByEmployeeUUID;
    public final DetailedActionType actionType;

    public ProductionRecord(LocalDateTime timestamp, Material material, int quantity, String recordedByEmployeeUUID, DetailedActionType actionType) {
        this.timestamp = timestamp;
        this.material = material;
        this.quantity = quantity;
        this.recordedByEmployeeUUID = recordedByEmployeeUUID;
        this.actionType = actionType;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("timestamp", timestamp.toString());
        map.put("material", material.name());
        map.put("quantity", quantity);
        map.put("recordedByEmployeeUUID", recordedByEmployeeUUID);
        map.put("actionType", actionType.name());
        return map;
    }

    public static ProductionRecord deserialize(Map<String, Object> map) {
        try {
            LocalDateTime ts = LocalDateTime.parse((String) map.get("timestamp"));
            Material mat = Material.matchMaterial((String) map.get("material"));
            int qty = ((Number) map.get("quantity")).intValue();
            String uuid = (String) map.get("recordedByEmployeeUUID");
            DetailedActionType at = DetailedActionType.valueOf((String) map.getOrDefault("actionType", DetailedActionType.BLOCK_BROKEN.name()));
            if (mat == null) {
                System.err.println("Material null lors de la désérialisation de ProductionRecord (global): " + map.get("material"));
                return null;
            }
            return new ProductionRecord(ts, mat, qty, uuid, at);
        } catch (Exception e) {
            System.err.println("Erreur désérialisation ProductionRecord (global): " + e.getMessage() + " pour map: " + map);
            return null;
        }
    }
}