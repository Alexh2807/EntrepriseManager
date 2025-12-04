package com.gravityyfh.roleplaycity.entreprise.model;

/**
 * Type d'action productive détaillée effectuée par un employé.
 */
public enum DetailedActionType {
    BLOCK_BROKEN("Bloc Cassé"),
    ITEM_CRAFTED("Item Crafté"),
    BLOCK_PLACED("Bloc Posé");

    private final String displayName;

    DetailedActionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
