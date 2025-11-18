package com.gravityyfh.roleplaycity.police.data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Données d'emprisonnement pour un joueur
 */
public class PrisonData {

    private final UUID playerUuid;
    private final String townName;
    private final String plotIdentifier;
    private final LocalDateTime imprisonmentStart;
    private final int durationMinutes;
    private final String reason;
    private final UUID imprisonedBy;

    // Pour permettre la modification de la durée (prolongation)
    private int currentDurationMinutes;

    public PrisonData(UUID playerUuid, String townName, String plotIdentifier,
                     LocalDateTime imprisonmentStart, int durationMinutes,
                     String reason, UUID imprisonedBy) {
        this.playerUuid = playerUuid;
        this.townName = townName;
        this.plotIdentifier = plotIdentifier;
        this.imprisonmentStart = imprisonmentStart;
        this.durationMinutes = durationMinutes;
        this.currentDurationMinutes = durationMinutes;
        this.reason = reason;
        this.imprisonedBy = imprisonedBy;
    }

    /**
     * Vérifie si la peine est terminée
     */
    public boolean isExpired() {
        LocalDateTime releaseTime = imprisonmentStart.plusMinutes(currentDurationMinutes);
        return LocalDateTime.now().isAfter(releaseTime);
    }

    /**
     * Calcule le temps restant en secondes
     */
    public long getRemainingSeconds() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime releaseTime = imprisonmentStart.plusMinutes(currentDurationMinutes);

        long seconds = java.time.Duration.between(now, releaseTime).getSeconds();
        return Math.max(0, seconds);
    }

    /**
     * Obtient le temps restant formaté (ex: "5m 30s")
     */
    public String getFormattedRemainingTime() {
        long totalSeconds = getRemainingSeconds();

        if (totalSeconds <= 0) {
            return "0s";
        }

        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    /**
     * Prolonge la durée de prison
     */
    public void extendDuration(int additionalMinutes) {
        this.currentDurationMinutes += additionalMinutes;
    }

    // Getters

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getTownName() {
        return townName;
    }

    public String getPlotIdentifier() {
        return plotIdentifier;
    }

    public LocalDateTime getImprisonmentStart() {
        return imprisonmentStart;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getCurrentDurationMinutes() {
        return currentDurationMinutes;
    }

    public String getReason() {
        return reason;
    }

    public UUID getImprisonedBy() {
        return imprisonedBy;
    }
}
