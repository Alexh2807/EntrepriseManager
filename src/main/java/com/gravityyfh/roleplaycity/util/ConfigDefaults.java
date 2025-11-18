package com.gravityyfh.roleplaycity.util;

/**
 * FIX BASSE #26: Centralisation des valeurs par défaut de configuration
 *
 * Cette classe regroupe toutes les valeurs par défaut utilisées lors du chargement
 * de config.yml pour éviter la duplication et faciliter la maintenance.
 */
public final class ConfigDefaults {

    private ConfigDefaults() {
        throw new UnsupportedOperationException("Utility class");
    }

    // === FINANCES ===

    /** Pourcentage de taxes appliqué sur les revenus (défaut: 15%) */
    public static final double FINANCE_POURCENTAGE_TAXES = 15.0;

    /** Charge salariale par employé par cycle horaire (défaut: 0€) */
    public static final double FINANCE_CHARGE_SALARIALE_PAR_EMPLOYE = 0.0;

    /** Montant de l'allocation chômage versée par heure (défaut: 0€) */
    public static final double FINANCE_ALLOCATION_CHOMAGE_HORAIRE = 0.0;

    /** Nombre maximum d'entreprises qu'un gérant peut gérer (défaut: 1) */
    public static final int FINANCE_MAX_ENTREPRISES_PAR_GERANT = 1;

    /** Nombre maximum d'emplois salariés simultanés par joueur (défaut: 1) */
    public static final int FINANCE_MAX_TRAVAIL_JOUEUR = 1;

    // === INVITATIONS ===

    /** Distance maximale pour envoyer une invitation en blocs (défaut: 10) */
    public static final double INVITATION_DISTANCE_MAX = 10.0;

    /** Distance maximale entre maire et gérant à la création (défaut: 15) */
    public static final double CREATION_DISTANCE_MAX_MAIRE_GERANT = 15.0;

    // === CV ===

    /** Durée d'expiration des demandes de CV en secondes (défaut: 60s) */
    public static final long CV_REQUEST_EXPIRATION_SECONDS = 60L;

    // === ENTREPRISE ===

    /** Taille maximale du log détaillé de production (défaut: 1000 entrées) */
    public static final int ENTREPRISE_MAX_DETAILED_PRODUCTION_LOG_SIZE = 1000;

    /** Coût de renommage d'une entreprise (défaut: 0€) */
    public static final double ENTREPRISE_RENAME_COST = 0.0;

    /** Coût de création par défaut si non spécifié dans types-entreprise (défaut: 0€) */
    public static final double ENTREPRISE_COUT_CREATION_DEFAULT = 0.0;

    // === PRODUCTION ===

    /** Valeur unitaire par défaut pour matériaux non configurés (défaut: 0€) */
    public static final double PRODUCTION_VALEUR_UNITAIRE_DEFAULT = 0.0;

    // === SYSTEM ===

    /** Intervalle d'auto-save en minutes (défaut: 10min) */
    public static final int SYSTEM_AUTOSAVE_INTERVAL_MINUTES = 10;

    /** Activer le mode debug avec logs détaillés (défaut: false) */
    public static final boolean SYSTEM_DEBUG_MODE = false;

    // === VALIDATION ===

    /** Longueur minimale d'un nom d'entreprise (défaut: 3) */
    public static final int VALIDATION_MIN_COMPANY_NAME_LENGTH = 3;

    /** Longueur maximale d'un nom d'entreprise (défaut: 32) */
    public static final int VALIDATION_MAX_COMPANY_NAME_LENGTH = 32;

    /** Longueur maximale d'une description de transaction (défaut: 256) */
    public static final int VALIDATION_MAX_TRANSACTION_DESC_LENGTH = 256;

    // === PERFORMANCE ===

    /** Durée de validité du cache de restrictions en millisecondes (défaut: 5min) */
    public static final long PERFORMANCE_RESTRICTIONS_CACHE_TTL_MS = 5 * 60 * 1000L;

    /** Intervalle de vérification d'activité des employés en ticks (défaut: 2h) */
    public static final long PERFORMANCE_ACTIVITY_CHECK_INTERVAL_TICKS = 144000L; // 2 heures

    /** Intervalle de vérification d'inactivité en ticks (défaut: 24h) */
    public static final long PERFORMANCE_INACTIVITY_CHECK_INTERVAL_TICKS = 1728000L; // 24 heures

    // === MÉTHODES UTILITAIRES ===

    /**
     * Retourne la valeur de configuration ou la valeur par défaut
     * @param configValue Valeur lue depuis config.yml (peut être null)
     * @param defaultValue Valeur par défaut
     * @return configValue si non-null, sinon defaultValue
     */
    public static <T> T getOrDefault(T configValue, T defaultValue) {
        return configValue != null ? configValue : defaultValue;
    }

    /**
     * Valide et borne une valeur numérique
     * @param value Valeur à valider
     * @param min Valeur minimale
     * @param max Valeur maximale
     * @param defaultValue Valeur par défaut si hors limites
     * @return value bornée entre min et max, ou defaultValue si invalide
     */
    public static double clamp(double value, double min, double max, double defaultValue) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return defaultValue;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Valide et borne une valeur entière
     * @param value Valeur à valider
     * @param min Valeur minimale
     * @param max Valeur maximale
     * @param defaultValue Valeur par défaut si hors limites
     * @return value bornée entre min et max
     */
    public static int clamp(int value, int min, int max, int defaultValue) {
        if (value < min || value > max) {
            return defaultValue;
        }
        return value;
    }
}
