package com.gravityyfh.roleplaycity.mdt.setup;

import org.bukkit.ChatColor;
import org.bukkit.Material;

/**
 * Modes de configuration disponibles pour le setup MDT
 */
public enum MDTSetupMode {
    // Spawns
    LOBBY("Spawn Lobby", Material.NETHER_STAR, ChatColor.GOLD,
          "Clic droit sur un bloc pour définir le spawn du lobby"),
    RED_SPAWN("Spawn Équipe Rouge", Material.RED_WOOL, ChatColor.RED,
              "Clic droit sur un bloc pour définir le spawn de l'équipe rouge"),
    BLUE_SPAWN("Spawn Équipe Bleue", Material.BLUE_WOOL, ChatColor.BLUE,
               "Clic droit sur un bloc pour définir le spawn de l'équipe bleue"),

    // Lits d'équipe
    RED_BED("Lit Équipe Rouge", Material.RED_BED, ChatColor.RED,
            "Clic droit sur un LIT ROUGE pour le définir comme lit de respawn"),
    BLUE_BED("Lit Équipe Bleue", Material.BLUE_BED, ChatColor.BLUE,
             "Clic droit sur un LIT BLEU pour le définir comme lit de respawn"),

    // Lits neutres
    NEUTRAL_BED("Lit Neutre (+2♥)", Material.WHITE_BED, ChatColor.WHITE,
                "Clic droit sur un LIT pour l'ajouter comme lit neutre (+2 cœurs)"),

    // Générateurs
    GENERATOR_BRICK("Générateur Brique", Material.BRICK, ChatColor.GOLD,
                    "Clic droit sur un bloc pour placer un générateur de briques (1s)"),
    GENERATOR_IRON("Générateur Fer", Material.IRON_INGOT, ChatColor.WHITE,
                   "Clic droit sur un bloc pour placer un générateur de fer (16s)"),
    GENERATOR_GOLD("Générateur Or", Material.GOLD_INGOT, ChatColor.YELLOW,
                   "Clic droit sur un bloc pour placer un générateur d'or (60s)"),
    GENERATOR_DIAMOND("Générateur Diamant", Material.DIAMOND, ChatColor.AQUA,
                      "Clic droit sur un bloc pour placer un générateur de diamant (240s)"),

    // Marchand
    MERCHANT("Marchand", Material.EMERALD, ChatColor.GREEN,
             "Clic droit sur un bloc pour définir la position du marchand"),

    // Suppression
    REMOVE("Supprimer", Material.BARRIER, ChatColor.RED,
           "Clic droit sur un élément configuré pour le supprimer");

    private final String displayName;
    private final Material icon;
    private final ChatColor color;
    private final String instruction;

    MDTSetupMode(String displayName, Material icon, ChatColor color, String instruction) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
        this.instruction = instruction;
    }

    public String getDisplayName() {
        return color + displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public ChatColor getColor() {
        return color;
    }

    public String getInstruction() {
        return instruction;
    }

    public String getColoredInstruction() {
        return ChatColor.GRAY + "➤ " + ChatColor.YELLOW + instruction;
    }
}
