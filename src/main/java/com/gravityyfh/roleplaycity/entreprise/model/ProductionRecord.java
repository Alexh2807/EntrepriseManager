package com.gravityyfh.roleplaycity.entreprise.model;

import org.bukkit.Material;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Record de production global d'une entreprise.
 * Enregistre quelle ressource a été produite, par quel employé, et quand.
 */
public record ProductionRecord(
    LocalDateTime timestamp,
    Material material,
    int quantity,
    String recordedByEmployeeUUID,
    DetailedActionType actionType
) {
    /**
     * Sérialise le record en Map pour sauvegarde YAML.
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("timestamp", timestamp.toString());
        map.put("material", material.name());
        map.put("quantity", quantity);
        map.put("recordedByEmployeeUUID", recordedByEmployeeUUID);
        map.put("actionType", actionType.name());
        return map;
    }

    /**
     * Désérialise un ProductionRecord depuis une Map YAML.
     * @return ProductionRecord ou null si erreur
     */
    public static ProductionRecord deserialize(Map<String, Object> map) {
        try {
            LocalDateTime ts = LocalDateTime.parse((String) map.get("timestamp"));
            Material mat = Material.matchMaterial((String) map.get("material"));
            int qty = ((Number) map.get("quantity")).intValue();
            String uuid = (String) map.get("recordedByEmployeeUUID");
            DetailedActionType at = DetailedActionType.valueOf(
                (String) map.getOrDefault("actionType", DetailedActionType.BLOCK_BROKEN.name())
            );

            if (mat == null) {
                System.err.println("Material null désérialisation ProductionRecord: " + map.get("material"));
                return null;
            }

            return new ProductionRecord(ts, mat, qty, uuid, at);
        } catch (Exception e) {
            System.err.println("Erreur désérialisation ProductionRecord: " + e.getMessage() + " pour map: " + map);
            return null;
        }
    }
}
