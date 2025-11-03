package com.gravityyfh.roleplaycity.town.data;

/**
 * Types de permissions pour les plots
 */
public enum PlotPermission {
    BUILD("Construire", "Placer/détruire des blocs"),
    INTERACT("Interagir", "Utiliser portes, coffres, etc."),
    SWITCH("Switches", "Leviers, boutons, plaques"),
    ITEM_USE("Utiliser items", "Seau, briquet, etc."),
    CONTAINER_ACCESS("Accès conteneurs", "Coffres, fourneaux, etc."),
    REDSTONE("Redstone", "Activer mécanismes redstone"),
    ENTITY_INTERACT("Interagir entités", "Cadres, supports d'armure"),
    VEHICLE_USE("Véhicules", "Bateaux, wagonnet"),
    ANIMALS("Animaux", "Tuer/élever animaux");

    private final String displayName;
    private final String description;

    PlotPermission(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
