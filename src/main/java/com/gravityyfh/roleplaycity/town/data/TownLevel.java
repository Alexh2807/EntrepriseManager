package com.gravityyfh.roleplaycity.town.data;

import org.bukkit.ChatColor;

/**
 * Enum représentant les niveaux d'évolution d'une ville
 *
 * Système de progression :
 * - CAMPEMENT : Ville de départ, petite communauté (1-4 joueurs)
 * - VILLAGE : Communauté moyenne (5-14 joueurs) avec services municipaux limités
 * - VILLE : Grande communauté (15+ joueurs) avec tous les services débloqués
 */
public enum TownLevel {
    CAMPEMENT(1,
        "Campement",
        ChatColor.GRAY + "⛺ Campement",
        "Une petite communauté naissante",
        1, 4),

    VILLAGE(2,
        "Village",
        ChatColor.GREEN + "🏘️ Village",
        "Une communauté établie avec des services de base",
        5, 14),

    VILLE(3,
        "Ville",
        ChatColor.GOLD + "🏛️ Ville",
        "Une grande ville prospère avec tous les services",
        15, Integer.MAX_VALUE);

    private final int numericLevel;
    private final String name;
    private final String displayName;
    private final String description;
    private final int minPopulation;
    private final int maxPopulation;

    TownLevel(int numericLevel, String name, String displayName, String description, int minPopulation, int maxPopulation) {
        this.numericLevel = numericLevel;
        this.name = name;
        this.displayName = displayName;
        this.description = description;
        this.minPopulation = minPopulation;
        this.maxPopulation = maxPopulation;
    }

    public int getNumericLevel() {
        return numericLevel;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public int getMinPopulation() {
        return minPopulation;
    }

    public int getMaxPopulation() {
        return maxPopulation;
    }

    /**
     * Vérifie si un nombre de joueurs est adapté à ce niveau
     */
    public boolean isValidPopulation(int population) {
        return population >= minPopulation && population <= maxPopulation;
    }

    /**
     * Récupère le niveau suivant, null si déjà au max
     */
    public TownLevel getNextLevel() {
        return switch (this) {
            case CAMPEMENT -> VILLAGE;
            case VILLAGE -> VILLE;
            case VILLE -> null; // Niveau maximum
        };
    }

    /**
     * Récupère le niveau précédent, null si déjà au min
     */
    public TownLevel getPreviousLevel() {
        return switch (this) {
            case CAMPEMENT -> null; // Niveau minimum
            case VILLAGE -> CAMPEMENT;
            case VILLE -> VILLAGE;
        };
    }

    /**
     * Vérifie si ce niveau permet d'avoir du personnel municipal
     */
    public boolean allowsMunicipalStaff() {
        return this != CAMPEMENT;
    }

    /**
     * Récupère le niveau à partir d'un numéro
     */
    public static TownLevel fromNumeric(int level) {
        for (TownLevel townLevel : values()) {
            if (townLevel.numericLevel == level) {
                return townLevel;
            }
        }
        return CAMPEMENT; // Par défaut
    }

    /**
     * Récupère le niveau à partir d'un nom
     */
    public static TownLevel fromName(String name) {
        for (TownLevel level : values()) {
            if (level.name.equalsIgnoreCase(name)) {
                return level;
            }
        }
        return null;
    }

    /**
     * Récupère le niveau approprié selon la population
     */
    public static TownLevel fromPopulation(int population) {
        for (TownLevel level : values()) {
            if (level.isValidPopulation(population)) {
                return level;
            }
        }
        return CAMPEMENT; // Par défaut
    }

    @Override
    public String toString() {
        return displayName;
    }
}