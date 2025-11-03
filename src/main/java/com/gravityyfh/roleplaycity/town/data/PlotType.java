package com.gravityyfh.roleplaycity.town.data;

import org.bukkit.Material;

public enum PlotType {
    PARTICULIER("🏠 Particulier", Material.RED_BED, 24.0, true, false),
    PROFESSIONNEL("🏢 Professionnel", Material.CRAFTING_TABLE, 48.0, true, true),
    MUNICIPAL("🏛️ Municipal", Material.STONE_BRICKS, 0.0, false, false),
    PUBLIC("⚙️ Public", Material.GRASS_BLOCK, 0.0, false, false);

    private final String displayName;
    private final Material icon;
    private final double dailyTax;
    private final boolean canBeSold;
    private final boolean requiresCompany;

    PlotType(String displayName, Material icon, double dailyTax, boolean canBeSold, boolean requiresCompany) {
        this.displayName = displayName;
        this.icon = icon;
        this.dailyTax = dailyTax;
        this.canBeSold = canBeSold;
        this.requiresCompany = requiresCompany;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public double getDailyTax() {
        return dailyTax;
    }

    public double getHourlyTax() {
        return dailyTax / 24.0;
    }

    public boolean canBeSold() {
        return canBeSold;
    }

    public boolean requiresCompany() {
        return requiresCompany;
    }
}
