package com.gravityyfh.roleplaycity.heist.data;

/**
 * Résultat final d'un cambriolage
 */
public enum HeistResult {
    /**
     * Bombe désamorcée par la police
     */
    DEFUSED("Désamorcé", false),

    /**
     * Explosion réussie, vol effectué
     */
    ROBBERY_SUCCESS("Vol réussi", true),

    /**
     * Temps de vol écoulé
     */
    TIME_EXPIRED("Temps écoulé", true),

    /**
     * Tous les cambrioleurs arrêtés pendant le countdown
     */
    ALL_ARRESTED_COUNTDOWN("Tous arrêtés (avant explosion)", false),

    /**
     * Tous les cambrioleurs arrêtés/tués pendant le vol
     */
    ALL_ARRESTED_ROBBERY("Tous arrêtés (pendant vol)", true),

    /**
     * Cambriolage annulé (admin, erreur, etc.)
     */
    CANCELLED("Annulé", false),

    /**
     * Serveur redémarré pendant le heist
     */
    SERVER_RESTART("Redémarrage serveur", false);

    private final String displayName;
    private final boolean explosionOccurred;

    HeistResult(String displayName, boolean explosionOccurred) {
        this.displayName = displayName;
        this.explosionOccurred = explosionOccurred;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * @return true si l'explosion a eu lieu
     */
    public boolean didExplode() {
        return explosionOccurred;
    }

    /**
     * @return true si les cambrioleurs ont "gagné" (vol effectué)
     */
    public boolean isSuccess() {
        return this == ROBBERY_SUCCESS || this == TIME_EXPIRED;
    }
}
