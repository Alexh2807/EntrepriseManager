package com.gravityyfh.roleplaycity.service;

import org.bukkit.boss.BarColor;

/**
 * Types de services professionnels disponibles
 * Chaque type a ses propres règles, skins et localisations
 */
public enum ProfessionalServiceType {

    POLICE("Police", BarColor.BLUE, "policier", "policiere"),
    MEDICAL("Médical", BarColor.GREEN, "medecin", "medecine"),
    JUDGE("Justice", BarColor.YELLOW, "juge", "jugefemme"),
    ENTERPRISE("Entreprise", BarColor.WHITE, null, null);

    private final String displayName;
    private final BarColor barColor;
    private final String defaultSkinHomme;
    private final String defaultSkinFemme;

    ProfessionalServiceType(String displayName, BarColor barColor, String defaultSkinHomme, String defaultSkinFemme) {
        this.displayName = displayName;
        this.barColor = barColor;
        this.defaultSkinHomme = defaultSkinHomme;
        this.defaultSkinFemme = defaultSkinFemme;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BarColor getBarColor() {
        return barColor;
    }

    public String getDefaultSkinHomme() {
        return defaultSkinHomme;
    }

    public String getDefaultSkinFemme() {
        return defaultSkinFemme;
    }

    /**
     * Vérifie si ce type de service utilise des skins
     */
    public boolean usesSkin() {
        return defaultSkinHomme != null && defaultSkinFemme != null;
    }

    /**
     * Retourne l'emoji associé au service
     */
    public String getEmoji() {
        return switch (this) {
            case POLICE -> "🚔";
            case MEDICAL -> "🏥";
            case JUDGE -> "⚖";
            case ENTERPRISE -> "🏢";
        };
    }
}
