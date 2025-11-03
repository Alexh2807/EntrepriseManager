package com.gravityyfh.roleplaycity.town.data;

public enum TownRole {
    MAIRE("Maire", 6, true),
    ADJOINT("Adjoint", 5, true),
    POLICIER("Policier", 4, false),
    JUGE("Juge", 3, false),
    ARCHITECTE("Architecte", 2, false),
    CITOYEN("Citoyen", 1, false);

    private final String displayName;
    private final int power;
    private final boolean canManageTown;

    TownRole(String displayName, int power, boolean canManageTown) {
        this.displayName = displayName;
        this.power = power;
        this.canManageTown = canManageTown;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getPower() {
        return power;
    }

    public boolean canManageTown() {
        return canManageTown;
    }

    public boolean hasPermission(TownPermission permission) {
        return switch (permission) {
            case MANAGE_TOWN -> this == MAIRE;
            case MANAGE_CLAIMS -> this == MAIRE || this == ADJOINT;
            case MANAGE_MEMBERS -> this == MAIRE || this == ADJOINT;
            case MANAGE_ECONOMY -> this == MAIRE || this == ADJOINT;
            case MANAGE_TAXES -> this == MAIRE || this == ADJOINT;
            case ISSUE_FINE -> this == POLICIER;
            case JUDGE_CASE -> this == JUGE;
            case BUILD_MUNICIPAL -> this == ARCHITECTE || this == MAIRE || this == ADJOINT;
            case VIEW_INFO -> true; // Tous les citoyens
            case PAY_TAXES -> true;
        };
    }

    public boolean canManageClaims() {
        return hasPermission(TownPermission.MANAGE_CLAIMS);
    }

    public enum TownPermission {
        MANAGE_TOWN,
        MANAGE_CLAIMS,
        MANAGE_MEMBERS,
        MANAGE_ECONOMY,
        MANAGE_TAXES,
        ISSUE_FINE,
        JUDGE_CASE,
        BUILD_MUNICIPAL,
        VIEW_INFO,
        PAY_TAXES
    }
}
