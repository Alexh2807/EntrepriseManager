package com.gravityyfh.roleplaycity.heist.data;

/**
 * Phases d'un cambriolage
 */
public enum HeistPhase {
    /**
     * Bombe posée, compte à rebours actif
     * La police peut tenter de désamorcer
     */
    COUNTDOWN("Compte à rebours"),

    /**
     * Explosion effectuée, vol en cours
     * Les cambrioleurs ont accès temporaire au terrain
     */
    ROBBERY("Vol en cours"),

    /**
     * Cambriolage terminé (défusé, temps écoulé, ou tous arrêtés)
     */
    ENDED("Terminé");

    private final String displayName;

    HeistPhase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
