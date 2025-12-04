package com.gravityyfh.roleplaycity.heist.data;

import org.bukkit.Location;

import java.util.UUID;

/**
 * Représente une bombe posée.
 * La bombe reste persistante tant qu'elle n'a pas explosé.
 * Après un restart serveur, elle peut être réarmée par clic droit.
 */
public class PlacedBomb {

    private final UUID bombId;
    private final UUID placedByUuid;
    private final String placedByName;
    private final Location location;
    private final UUID armorStandId;
    private final long placedAtTimestamp;

    // Info terrain (null si hors ville)
    private String townName;
    private String plotKey;
    private String plotId;

    // État de confirmation
    private boolean awaitingConfirmation = false;

    // État du timer (pour persistance après restart)
    private boolean timerStarted = false;
    private int remainingSeconds = -1; // -1 = pas encore démarré
    private long timerStartTimestamp = 0; // Timestamp quand le timer a été lancé

    public PlacedBomb(UUID placedByUuid, String placedByName, Location location, UUID armorStandId) {
        this.bombId = UUID.randomUUID();
        this.placedByUuid = placedByUuid;
        this.placedByName = placedByName;
        this.location = location;
        this.armorStandId = armorStandId;
        this.placedAtTimestamp = System.currentTimeMillis();
    }

    /**
     * Constructeur avec bombId spécifique (pour le chargement depuis fichier)
     */
    public PlacedBomb(UUID bombId, UUID placedByUuid, String placedByName, Location location, UUID armorStandId, long placedAtTimestamp) {
        this.bombId = bombId;
        this.placedByUuid = placedByUuid;
        this.placedByName = placedByName;
        this.location = location;
        this.armorStandId = armorStandId;
        this.placedAtTimestamp = placedAtTimestamp;
    }

    /**
     * @return true si la bombe est sur un terrain claimé
     */
    public boolean isOnClaimedPlot() {
        return townName != null;
    }

    // =========================================================================
    // GETTERS
    // =========================================================================

    public UUID getBombId() {
        return bombId;
    }

    public UUID getPlacedByUuid() {
        return placedByUuid;
    }

    public String getPlacedByName() {
        return placedByName;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getArmorStandId() {
        return armorStandId;
    }

    public long getPlacedAtTimestamp() {
        return placedAtTimestamp;
    }

    public String getTownName() {
        return townName;
    }

    public String getPlotKey() {
        return plotKey;
    }

    public String getPlotId() {
        return plotId;
    }

    public boolean isAwaitingConfirmation() {
        return awaitingConfirmation;
    }

    // =========================================================================
    // SETTERS
    // =========================================================================

    public void setTownName(String townName) {
        this.townName = townName;
    }

    public void setPlotKey(String plotKey) {
        this.plotKey = plotKey;
    }

    public void setPlotId(String plotId) {
        this.plotId = plotId;
    }

    public void setAwaitingConfirmation(boolean awaitingConfirmation) {
        this.awaitingConfirmation = awaitingConfirmation;
    }

    // =========================================================================
    // GETTERS/SETTERS TIMER
    // =========================================================================

    public boolean isTimerStarted() {
        return timerStarted;
    }

    public void setTimerStarted(boolean timerStarted) {
        this.timerStarted = timerStarted;
    }

    public int getRemainingSeconds() {
        return remainingSeconds;
    }

    public void setRemainingSeconds(int remainingSeconds) {
        this.remainingSeconds = remainingSeconds;
    }

    public long getTimerStartTimestamp() {
        return timerStartTimestamp;
    }

    public void setTimerStartTimestamp(long timerStartTimestamp) {
        this.timerStartTimestamp = timerStartTimestamp;
    }

    /**
     * @return true si la bombe avait un timer actif avant le restart
     * Après un restart, le timer sera réinitialisé car on ne peut pas
     * garantir la précision du temps écoulé
     */
    public boolean hadActiveTimer() {
        return timerStarted && remainingSeconds > 0;
    }

    /**
     * Réinitialise l'état du timer (pour après un restart)
     */
    public void resetTimerState() {
        this.timerStarted = false;
        this.remainingSeconds = -1;
        this.timerStartTimestamp = 0;
        this.awaitingConfirmation = false;
    }
}
