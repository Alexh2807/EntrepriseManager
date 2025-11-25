package com.gravityyfh.roleplaycity.town.data;

/**
 * Flags de protection pour les plots
 */
public enum PlotFlag {
    PVP("PVP", "Combat joueur vs joueur", true),
    EXPLOSION("Explosions", "Dégâts d'explosions (creepers, TNT)", false),
    FIRE_SPREAD("Propagation feu", "Le feu se propage", false),
    MOB_SPAWNING("Spawn mobs", "Mobs hostiles peuvent spawn", false),
    LEAF_DECAY("Feuilles se détériorent", "Feuilles tombent naturellement", true),
    CROP_TRAMPLING("Piétiner cultures", "Détruire cultures en sautant", false),
    ENTITY_GRIEFING("Grief entités", "Endermen, ghasts détruisent blocs", false),
    LIQUID_FLOW("Écoulement liquides", "Eau et lave s'écoulent", true),
    REDSTONE("Redstone", "Circuits redstone fonctionnent", true),
    WEATHER_CHANGE("Changement météo", "Météo affecte le plot", true);

    private final String displayName;
    private final String description;
    private final boolean defaultValue;

    PlotFlag(String displayName, String description, boolean defaultValue) {
        this.displayName = displayName;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }
}
