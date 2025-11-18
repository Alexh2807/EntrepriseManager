package com.gravityyfh.roleplaycity.shop;

/**
 * État d'une boutique
 */
public enum ShopStatus {
    /**
     * Boutique active avec du stock disponible
     */
    ACTIVE,

    /**
     * Boutique fonctionnelle mais sans stock
     */
    OUT_OF_STOCK,

    /**
     * Boutique cassée (composant manquant comme coffre ou panneau)
     */
    BROKEN,

    /**
     * Boutique désactivée manuellement par le propriétaire
     */
    DISABLED
}