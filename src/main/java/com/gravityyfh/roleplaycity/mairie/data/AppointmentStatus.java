package com.gravityyfh.roleplaycity.mairie.data;

/**
 * Statut d'un rendez-vous à la mairie
 */
public enum AppointmentStatus {
    PENDING("En attente"),
    TREATED("Traité");

    private final String displayName;

    AppointmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static AppointmentStatus fromString(String value) {
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PENDING;
        }
    }
}
