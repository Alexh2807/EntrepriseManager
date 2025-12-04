package com.gravityyfh.roleplaycity.service;

/**
 * Représente un niveau de restriction pour une entreprise
 */
public class RestrictionLevel {
    private final int level;
    private final int quotaPerHour;
    private final double upgradeCost;
    private final String displayName;

    public RestrictionLevel(int level, int quotaPerHour, double upgradeCost, String displayName) {
        this.level = level;
        this.quotaPerHour = quotaPerHour;
        this.upgradeCost = upgradeCost;
        this.displayName = displayName;
    }

    public int getLevel() {
        return level;
    }

    public int getQuotaPerHour() {
        return quotaPerHour;
    }

    public double getUpgradeCost() {
        return upgradeCost;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName + " (Niveau " + level + " - " + quotaPerHour + "/h)";
    }
}
