package com.gravityyfh.roleplaycity.entreprise.model;

import org.bukkit.Material;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Record détaillé d'une action productive d'un employé.
 * Enregistre le type d'action, le matériau, la quantité et le timestamp.
 */
public record DetailedProductionRecord(
    LocalDateTime timestamp,
    DetailedActionType actionType,
    Material material,
    int quantity
) {
    /**
     * Constructeur simplifié avec timestamp = maintenant.
     */
    public DetailedProductionRecord(DetailedActionType actionType, Material material, int quantity) {
        this(LocalDateTime.now(), actionType, material, quantity);
    }

    /**
     * Sérialise le record en Map pour sauvegarde YAML.
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("timestamp", timestamp.toString());
        map.put("actionType", actionType.name());
        map.put("material", material.name());
        map.put("quantity", quantity);
        return map;
    }

    /**
     * Désérialise un record depuis une Map YAML.
     * @return DetailedProductionRecord ou null si erreur
     */
    public static DetailedProductionRecord deserialize(Map<String, Object> map) {
        try {
            LocalDateTime ts = LocalDateTime.parse((String) map.get("timestamp"));
            DetailedActionType dat = DetailedActionType.valueOf((String) map.get("actionType"));
            Material mat = Material.matchMaterial((String) map.get("material"));
            int qty = ((Number) map.get("quantity")).intValue();

            if (mat == null) {
                System.err.println("Material null désérialisation DetailedProductionRecord: " + map.get("material"));
                return null;
            }

            return new DetailedProductionRecord(ts, dat, mat, qty);
        } catch (Exception e) {
            System.err.println("Erreur désérialisation DetailedProductionRecord: " + e.getMessage() + " pour map: " + map);
            return null;
        }
    }
}
