package com.gravityyfh.roleplaycity.town.data;

import org.bukkit.Material;

public enum MunicipalSubType {
    NONE("Aucun", Material.BARRIER),
    MAIRIE("🏛️ Mairie", Material.BEACON),
    COMMISSARIAT("🚓 Commissariat", Material.IRON_BARS),
    TRIBUNAL("⚖️ Tribunal", Material.ANVIL),
    LA_POSTE("📮 La Poste", Material.CHEST);

    private final String displayName;
    private final Material icon;

    MunicipalSubType(String displayName, Material icon) {
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getIcon() {
        return icon;
    }

    public boolean requiresRole(TownRole role) {
        return switch (this) {
            case MAIRIE -> role == TownRole.MAIRE || role == TownRole.ADJOINT;
            case COMMISSARIAT -> role == TownRole.POLICIER;
            case TRIBUNAL -> role == TownRole.JUGE;
            case LA_POSTE -> true; // Accessible à tous les citoyens
            case NONE -> true;
        };
    }
}
