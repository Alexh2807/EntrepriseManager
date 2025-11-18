package com.gravityyfh.roleplaycity.shop;

/**
 * Action de réparation suggérée après validation
 */
public enum RepairAction {
    /**
     * Aucune action nécessaire
     */
    NONE,

    /**
     * Réparer les composants manquants
     */
    REPAIR,

    /**
     * Supprimer le shop
     */
    DELETE,

    /**
     * Mettre à jour le statut
     */
    UPDATE_STATUS,

    /**
     * Notifier le propriétaire
     */
    NOTIFY
}
