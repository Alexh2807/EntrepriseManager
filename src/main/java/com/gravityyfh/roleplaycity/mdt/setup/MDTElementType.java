package com.gravityyfh.roleplaycity.mdt.setup;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * Types d'éléments configurables pour le MDT Rush
 */
public enum MDTElementType {
    // === SPAWNS ===
    SPAWN_LOBBY("Spawn Lobby", Material.NETHER_STAR, ChatColor.GOLD,
        "Point d'arrivée des joueurs dans le lobby"),
    SPAWN_RED("Spawn Équipe Rouge", Material.RED_WOOL, ChatColor.RED,
        "Point de spawn de l'équipe rouge"),
    SPAWN_BLUE("Spawn Équipe Bleue", Material.BLUE_WOOL, ChatColor.BLUE,
        "Point de spawn de l'équipe bleue"),

    // === LITS ===
    BED_RED("Lit Équipe Rouge", Material.RED_BED, ChatColor.RED,
        "Lit de respawn de l'équipe rouge"),
    BED_BLUE("Lit Équipe Bleue", Material.BLUE_BED, ChatColor.BLUE,
        "Lit de respawn de l'équipe bleue"),
    BED_NEUTRAL("Lit Neutre (+2♥)", Material.WHITE_BED, ChatColor.WHITE,
        "Lit bonus qui donne +2 cœurs max"),

    // === GÉNÉRATEURS ===
    GEN_BRICK("Générateur Brique", Material.BRICK, ChatColor.GOLD,
        "Génère des briques toutes les secondes"),
    GEN_IRON("Générateur Fer", Material.IRON_INGOT, ChatColor.WHITE,
        "Génère du fer toutes les 16 secondes"),
    GEN_GOLD("Générateur Or", Material.GOLD_INGOT, ChatColor.YELLOW,
        "Génère de l'or toutes les 60 secondes"),
    GEN_DIAMOND("Générateur Diamant", Material.DIAMOND, ChatColor.AQUA,
        "Génère des diamants toutes les 240 secondes"),

    // === MARCHANDS (4 types) ===
    MERCHANT_BLOCKS("Marchand Blocs", Material.END_STONE, ChatColor.GRAY,
        "Vend des blocs de construction"),
    MERCHANT_WEAPONS("Marchand Armes", Material.IRON_SWORD, ChatColor.RED,
        "Vend des épées et arcs"),
    MERCHANT_ARMOR("Marchand Armures", Material.IRON_CHESTPLATE, ChatColor.BLUE,
        "Vend des armures et boucliers"),
    MERCHANT_SPECIAL("Marchand Spécial", Material.TNT, ChatColor.LIGHT_PURPLE,
        "Vend TNT, dynamite, outils"),

    // === SUPPRIMER ===
    REMOVE("Supprimer cet élément", Material.BARRIER, ChatColor.DARK_RED,
        "Retire cet élément de la configuration");

    private final String displayName;
    private final Material icon;
    private final ChatColor color;
    private final String description;

    MDTElementType(String displayName, Material icon, ChatColor color, String description) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.description = description;
    }

    public String getDisplayName() {
        return color + displayName;
    }

    public String getRawName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public ChatColor getColor() {
        return color;
    }

    public String getDescription() {
        return description;
    }

    public boolean isSpawn() {
        return this == SPAWN_LOBBY || this == SPAWN_RED || this == SPAWN_BLUE;
    }

    public boolean isBed() {
        return this == BED_RED || this == BED_BLUE || this == BED_NEUTRAL;
    }

    public boolean isGenerator() {
        return this == GEN_BRICK || this == GEN_IRON || this == GEN_GOLD || this == GEN_DIAMOND;
    }

    public boolean isMerchant() {
        return this == MERCHANT_BLOCKS || this == MERCHANT_WEAPONS ||
               this == MERCHANT_ARMOR || this == MERCHANT_SPECIAL;
    }

    public String getGeneratorType() {
        return switch (this) {
            case GEN_BRICK -> "brick";
            case GEN_IRON -> "iron";
            case GEN_GOLD -> "gold";
            case GEN_DIAMOND -> "diamond";
            default -> null;
        };
    }

    public String getMerchantType() {
        return switch (this) {
            case MERCHANT_BLOCKS -> "blocks";
            case MERCHANT_WEAPONS -> "weapons";
            case MERCHANT_ARMOR -> "armor";
            case MERCHANT_SPECIAL -> "special";
            default -> null;
        };
    }
}
