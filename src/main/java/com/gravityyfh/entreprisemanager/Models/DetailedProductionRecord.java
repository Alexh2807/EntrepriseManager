package com.gravityyfh.entreprisemanager.Models;

import org.bukkit.Material;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class DetailedProductionRecord {
    public final LocalDateTime timestamp;
    public final DetailedActionType actionType;
    public final Material material;
    public final int quantity;

    public DetailedProductionRecord(DetailedActionType actionType, Material material, int quantity) {
        this.timestamp = LocalDateTime.now();
        this.actionType = actionType;
        this.material = material;
        this.quantity = quantity;
    }

    public DetailedProductionRecord(LocalDateTime timestamp, DetailedActionType actionType, Material material, int quantity) {
        this.timestamp = timestamp;
        this.actionType = actionType;
        this.material = material;
        this.quantity = quantity;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("timestamp", timestamp.toString());
        map.put("actionType", actionType.name());
        map.put("material", material.name());
        map.put("quantity", quantity);
        return map;
    }

    public static DetailedProductionRecord deserialize(Map<String, Object> map) {
        try {
            LocalDateTime ts = LocalDateTime.parse((String) map.get("timestamp"));
            DetailedActionType dat = DetailedActionType.valueOf((String) map.get("actionType"));
            Material mat = Material.matchMaterial((String) map.get("material"));
            int qty = ((Number) map.get("quantity")).intValue();
            if (mat == null) {
                System.err.println("Material null lors de la désérialisation de DetailedProductionRecord: " + map.get("material"));
                return null;
            }
            return new DetailedProductionRecord(ts, dat, mat, qty);
        } catch (Exception e) {
            System.err.println("Erreur désérialisation DetailedProductionRecord: " + e.getMessage() + " pour map: " + map);
            return null;
        }
    }
}