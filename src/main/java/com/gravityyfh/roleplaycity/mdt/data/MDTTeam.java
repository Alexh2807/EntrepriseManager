package com.gravityyfh.roleplaycity.mdt.data;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;

/**
 * Équipes du MDT Rush
 */
public enum MDTTeam {
    RED("Rouge", ChatColor.RED, Color.RED, DyeColor.RED, Material.RED_BED, Material.RED_WOOL),
    BLUE("Bleu", ChatColor.BLUE, Color.BLUE, DyeColor.BLUE, Material.BLUE_BED, Material.BLUE_WOOL);

    private final String displayName;
    private final ChatColor chatColor;
    private final Color color;
    private final DyeColor dyeColor;
    private final Material bedMaterial;
    private final Material woolMaterial;

    MDTTeam(String displayName, ChatColor chatColor, Color color, DyeColor dyeColor,
            Material bedMaterial, Material woolMaterial) {
        this.displayName = displayName;
        this.chatColor = chatColor;
        this.color = color;
        this.dyeColor = dyeColor;
        this.bedMaterial = bedMaterial;
        this.woolMaterial = woolMaterial;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColoredName() {
        return chatColor + displayName;
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public Color getColor() {
        return color;
    }

    public DyeColor getDyeColor() {
        return dyeColor;
    }

    public Material getBedMaterial() {
        return bedMaterial;
    }

    public Material getWoolMaterial() {
        return woolMaterial;
    }

    /**
     * Retourne l'équipe opposée
     */
    public MDTTeam getOpposite() {
        return this == RED ? BLUE : RED;
    }
}
