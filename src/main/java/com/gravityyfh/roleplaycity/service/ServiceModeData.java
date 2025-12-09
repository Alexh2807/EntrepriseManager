package com.gravityyfh.roleplaycity.service;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Données du mode service d'un employé
 */
public class ServiceModeData implements ConfigurationSerializable {

    private final UUID playerUUID;
    private final String playerName;
    private String activeEnterprise; // Nom de l'entreprise en service
    private boolean active;
    private long activatedTime; // Timestamp d'activation
    private double earnedThisHour; // Montant gagné cette heure

    public ServiceModeData(UUID playerUUID, String playerName) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.active = false;
        this.activeEnterprise = null;
        this.activatedTime = 0;
        this.earnedThisHour = 0.0;
    }

    // Constructor pour désérialisation
    public ServiceModeData(Map<String, Object> data) {
        this.playerUUID = UUID.fromString((String) data.get("uuid"));
        this.playerName = (String) data.get("name");
        this.active = (Boolean) data.getOrDefault("active", false);
        this.activeEnterprise = (String) data.get("enterprise");
        this.activatedTime = ((Number) data.getOrDefault("activatedTime", 0L)).longValue();
        this.earnedThisHour = ((Number) data.getOrDefault("earnedThisHour", 0.0)).doubleValue();
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> data = new HashMap<>();
        data.put("uuid", playerUUID.toString());
        data.put("name", playerName);
        data.put("active", active);
        data.put("enterprise", activeEnterprise);
        data.put("activatedTime", activatedTime);
        data.put("earnedThisHour", earnedThisHour);
        return data;
    }

    public void activate(String enterpriseName) {
        this.active = true;
        this.activeEnterprise = enterpriseName;
        this.activatedTime = System.currentTimeMillis();
    }

    public void deactivate() {
        this.active = false;
        this.activeEnterprise = null;
        this.activatedTime = 0;
    }

    public void addEarnings(double amount) {
        this.earnedThisHour += amount;
    }

    public void resetHourlyEarnings() {
        this.earnedThisHour = 0.0;
    }

    public long getServiceDuration() {
        if (!active || activatedTime == 0) return 0;
        return System.currentTimeMillis() - activatedTime;
    }

    // Getters
    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public boolean isActive() { return active; }
    public String getActiveEnterprise() { return activeEnterprise; }
    public long getActivatedTime() { return activatedTime; }
    public double getEarnedThisHour() { return earnedThisHour; }

    public void setActiveEnterprise(String enterprise) {
        this.activeEnterprise = enterprise;
        this.active = (enterprise != null);
    }

    public void setStartTime(java.time.LocalDateTime startTime) {
        this.activatedTime = java.sql.Timestamp.valueOf(startTime).getTime();
    }

    public void setTotalEarned(double earned) {
        this.earnedThisHour = earned;
    }

    public java.time.LocalDateTime getStartTime() {
        return this.activatedTime > 0 ? new java.sql.Timestamp(this.activatedTime).toLocalDateTime() : null;
    }

    public java.time.LocalDateTime getEndTime() {
        return null; // Active session has no end time yet
    }

    public double getTotalEarned() {
        return this.earnedThisHour;
    }

    public static ServiceModeData deserialize(Map<String, Object> data) {
        return new ServiceModeData(data);
    }
}
