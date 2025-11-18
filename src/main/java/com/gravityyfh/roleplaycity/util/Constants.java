package com.gravityyfh.roleplaycity.util;

/**
 * FIX BASSE #13: Centralisation des constantes pour éviter les "magic numbers"
 * Cette classe regroupe toutes les constantes utilisées dans le plugin
 */
public final class Constants {

    private Constants() {
        throw new UnsupportedOperationException("Classe utilitaire - pas d'instanciation");
    }

    // ====================
    // DÉLAIS TEMPORELS (ms)
    // ====================

    /** Délai anti-spam pour création de boutiques (5 secondes) */
    public static final long SHOP_CREATION_COOLDOWN_MS = 5000L;

    /** Délai anti-double-click dans les GUIs (500ms) */
    public static final long GUI_CLICK_DELAY_MS = 500L;

    /** Délai anti-spam pour les commandes (500ms) */
    public static final long COMMAND_COOLDOWN_MS = 500L;

    /** Délai pickup pour display items des shops (32 secondes) */
    public static final int SHOP_DISPLAY_PICKUP_DELAY = 32000;

    /** TTL du cache des restrictions (5 minutes) */
    public static final long RESTRICTIONS_CACHE_TTL_MS = 300_000L;

    /** Timeout pour confirmation de retrait (30 secondes) */
    public static final long WITHDRAWAL_CONFIRMATION_TIMEOUT_MS = 30_000L;

    /** Timeout pour confirmation de licenciement (30 secondes) */
    public static final long FIRING_CONFIRMATION_TIMEOUT_MS = 30_000L;

    // ====================
    // LIMITES NUMÉRIQUES
    // ====================

    /** Montant minimum pour transactions */
    public static final double MIN_TRANSACTION_AMOUNT = 1.0;

    /** Montant maximum pour transactions (10 millions) */
    public static final double MAX_TRANSACTION_AMOUNT = 10_000_000.0;

    /** Seuil de confirmation pour retraits importants */
    public static final double WITHDRAWAL_CONFIRMATION_THRESHOLD = 1000.0;

    /** Prix maximum par boutique (1 million) */
    public static final double MAX_SHOP_PRICE = 1_000_000.0;

    /** Quantité maximum par vente dans shop (1 stack) */
    public static final int MAX_SHOP_QUANTITY = 64;

    /** Nombre maximum d'employés par entreprise */
    public static final int MAX_EMPLOYEES = 100;

    /** Prime horaire maximum (10k/h) */
    public static final double MAX_HOURLY_BONUS = 10_000.0;

    // ====================
    // LIMITES DE COLLECTIONS
    // ====================

    /** Maximum de transactions dans l'historique */
    public static final int MAX_TRANSACTION_LOG_SIZE = 10_000;

    /** Maximum de records de production */
    public static final int MAX_PRODUCTION_RECORDS = 50_000;

    /** Maximum d'activity records par entreprise */
    public static final int MAX_ACTIVITY_RECORDS = 150;

    /** Taille par défaut du log de transactions */
    public static final int DEFAULT_TRANSACTION_LOG_SIZE = 200;

    // ====================
    // SEUILS DE DÉTECTION
    // ====================

    /** Solde maximum raisonnable (100 millions) - au-delà = warning exploit */
    public static final double MAX_REASONABLE_BALANCE = 100_000_000.0;

    /** Chiffre d'affaires maximum raisonnable (1 milliard) */
    public static final double MAX_REASONABLE_CA = 1_000_000_000.0;

    // ====================
    // DÉLAIS D'INACTIVITÉ
    // ====================

    /** Jours d'inactivité avant kick automatique */
    public static final int INACTIVITY_KICK_DAYS = 30;

    /** Délai entre vérifications d'inactivité (1 heure) */
    public static final long INACTIVITY_CHECK_INTERVAL_TICKS = 72000L; // 20 ticks/sec * 3600 sec

    // ====================
    // ITEMS PAR PAGE (GUIs)
    // ====================

    /** Items par page pour menus standards */
    public static final int ITEMS_PER_PAGE_DEFAULT = 36;

    /** Items par page pour sélection de matériaux */
    public static final int ITEMS_PER_PAGE_MATERIALS = 45;

    // ====================
    // VALIDATION
    // ====================

    /** Longueur minimum d'un nom d'entreprise */
    public static final int MIN_COMPANY_NAME_LENGTH = 3;

    /** Longueur maximum d'un nom d'entreprise */
    public static final int MAX_COMPANY_NAME_LENGTH = 30;

    /** Pattern pour validation nom d'entreprise (alphanumérique + espaces + tirets) */
    public static final String COMPANY_NAME_PATTERN = "^[a-zA-Z0-9À-ÿ\\s\\-']+$";

    // ====================
    // MESSAGES ET FORMAT
    // ====================

    /** Largeur de ligne pour séparateurs décoratifs */
    public static final int SEPARATOR_WIDTH = 52;

    /** Format de date pour affichage */
    public static final String DATE_FORMAT = "dd/MM/yyyy HH:mm";

    /** Format de devise */
    public static final String CURRENCY_FORMAT = "%.2f€";
}
