package com.gravityyfh.entreprisemanager.Models;

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