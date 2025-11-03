package com.gravityyfh.roleplaycity.town.data;

import org.bukkit.Location;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Représente un groupe de parcelles assemblées
 * Permet de gérer plusieurs chunks comme une seule unité pour la location/vente
 */
public class PlotGroup {

    private final String groupId;
    private final String townName;
    private final Set<String> plotKeys; // Format: "world:chunkX:chunkZ"
    private final LocalDateTime creationDate;

    // Informations communes au groupe
    private String groupName;
    private UUID ownerUuid;
    private String ownerName;

    // Location/Vente (appliqué à tout le groupe)
    private boolean forRent;
    private boolean forSale;
    private double rentPricePerDay;
    private double salePrice;

    // Locataire actuel (s'applique à toutes les parcelles du groupe)
    private UUID renterUuid;
    private int rentDaysRemaining;
    private LocalDateTime rentStartDate;
    private LocalDateTime lastRentUpdate;

    public PlotGroup(String groupId, String townName) {
        this.groupId = groupId;
        this.townName = townName;
        this.plotKeys = new HashSet<>();
        this.creationDate = LocalDateTime.now();
        this.forRent = false;
        this.forSale = false;
        this.rentDaysRemaining = 0;
    }

    /**
     * Ajoute une parcelle au groupe
     */
    public boolean addPlot(Plot plot) {
        if (!plot.getTownName().equals(townName)) {
            return false; // La parcelle doit appartenir à la même ville
        }

        String plotKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();
        return plotKeys.add(plotKey);
    }

    /**
     * Retire une parcelle du groupe
     */
    public boolean removePlot(Plot plot) {
        String plotKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();
        return plotKeys.remove(plotKey);
    }

    /**
     * Vérifie si une parcelle fait partie du groupe
     */
    public boolean containsPlot(Plot plot) {
        String plotKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();
        return plotKeys.contains(plotKey);
    }

    /**
     * Vérifie si le groupe a au moins 2 parcelles
     */
    public boolean isValid() {
        return plotKeys.size() >= 2;
    }

    /**
     * Calcule le prix total de location par jour pour toutes les parcelles
     */
    public double calculateTotalRentPrice(List<Plot> plots) {
        return plots.stream()
                .filter(this::containsPlot)
                .mapToDouble(Plot::getRentPricePerDay)
                .sum();
    }

    /**
     * Calcule le prix total de vente pour toutes les parcelles
     */
    public double calculateTotalSalePrice(List<Plot> plots) {
        return plots.stream()
                .filter(this::containsPlot)
                .mapToDouble(Plot::getSalePrice)
                .sum();
    }

    /**
     * Recharge le solde de location (max 30 jours)
     */
    public int rechargeDays(int daysToAdd) {
        int maxDays = 30;
        int newTotal = this.rentDaysRemaining + daysToAdd;
        if (newTotal > maxDays) {
            int actualAdded = maxDays - this.rentDaysRemaining;
            this.rentDaysRemaining = maxDays;
            return actualAdded;
        }
        this.rentDaysRemaining = newTotal;
        return daysToAdd;
    }

    /**
     * Met à jour le solde de jours (appelé automatiquement)
     */
    public void updateRentDays() {
        if (lastRentUpdate == null || renterUuid == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long daysPassed = java.time.Duration.between(lastRentUpdate, now).toDays();

        if (daysPassed > 0) {
            rentDaysRemaining -= (int) daysPassed;
            lastRentUpdate = now;

            if (rentDaysRemaining <= 0) {
                clearRenter();
            }
        }
    }

    /**
     * Définit le locataire du groupe
     */
    public void setRenter(UUID renterUuid, int days) {
        this.renterUuid = renterUuid;
        this.rentDaysRemaining = Math.min(days, 30);
        this.rentStartDate = LocalDateTime.now();
        this.lastRentUpdate = LocalDateTime.now();
    }

    /**
     * Retire le locataire actuel
     */
    public void clearRenter() {
        this.renterUuid = null;
        this.rentDaysRemaining = 0;
        this.rentStartDate = null;
        this.lastRentUpdate = null;
    }

    /**
     * Vérifie si le groupe est loué par ce joueur
     */
    public boolean isRentedBy(UUID playerUuid) {
        return renterUuid != null && renterUuid.equals(playerUuid);
    }

    /**
     * Vérifie si le groupe appartient à ce joueur
     */
    public boolean isOwnedBy(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    // ========== Getters et Setters ==========

    public String getGroupId() {
        return groupId;
    }

    public String getTownName() {
        return townName;
    }

    public Set<String> getPlotKeys() {
        return new HashSet<>(plotKeys);
    }

    public int getPlotCount() {
        return plotKeys.size();
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isForRent() {
        return forRent;
    }

    public void setForRent(boolean forRent) {
        this.forRent = forRent;
    }

    public boolean isForSale() {
        return forSale;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
    }

    public double getRentPricePerDay() {
        return rentPricePerDay;
    }

    public void setRentPricePerDay(double rentPricePerDay) {
        this.rentPricePerDay = rentPricePerDay;
    }

    public double getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(double salePrice) {
        this.salePrice = salePrice;
    }

    public UUID getRenterUuid() {
        return renterUuid;
    }

    public int getRentDaysRemaining() {
        return rentDaysRemaining;
    }

    public LocalDateTime getRentStartDate() {
        return rentStartDate;
    }

    public LocalDateTime getLastRentUpdate() {
        return lastRentUpdate;
    }

    public void setLastRentUpdate(LocalDateTime lastRentUpdate) {
        this.lastRentUpdate = lastRentUpdate;
    }
}
