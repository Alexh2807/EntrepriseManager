package com.gravityyfh.roleplaycity.integration;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.identity.data.Identity;
import com.gravityyfh.roleplaycity.identity.manager.IdentityManager;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * PlaceholderAPI expansion for RoleplayCity
 * Provides placeholders for town information and player ranks
 */
public class RoleplayCityPlaceholders extends PlaceholderExpansion {

    private final RoleplayCity plugin;
    private final TownManager townManager;

    public RoleplayCityPlaceholders(RoleplayCity plugin) {
        this.plugin = plugin;
        this.townManager = plugin.getTownManager();
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "roleplaycity";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Required to stay loaded
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        if (player == null) {
            return "";
        }

        UUID playerUuid = player.getUniqueId();

        // %roleplaycity_town_name%
        if (identifier.equals("town_name")) {
            String townName = townManager.getPlayerTown(playerUuid);
            return townName != null ? townName : "SDF";
        }

        // %roleplaycity_town_rank%
        if (identifier.equals("town_rank")) {
            return getPlayerRank(playerUuid, false);
        }

        // %roleplaycity_town_rank_colored%
        if (identifier.equals("town_rank_colored")) {
            return getPlayerRank(playerUuid, true);
        }

        // ═══════════════════════════════════════════════════════════
        // PLACEHOLDERS D'IDENTITÉ
        // ═══════════════════════════════════════════════════════════

        // %roleplaycity_identity_name% → Nom Minecraft du joueur
        if (identifier.equals("identity_name") || identifier.equals("identity_fullname")) {
            return player.getName();
        }

        // %roleplaycity_identity_age% → "25"
        if (identifier.equals("identity_age")) {
            Identity identity = getIdentity(playerUuid);
            if (identity != null) {
                return String.valueOf(identity.getAge());
            }
            return "";
        }

        // %roleplaycity_identity_sex% → "Homme" ou "Femme"
        if (identifier.equals("identity_sex")) {
            Identity identity = getIdentity(playerUuid);
            if (identity != null && identity.getSex() != null) {
                return identity.getSex();
            }
            return "";
        }

        // %roleplaycity_identity_height% → "175"
        if (identifier.equals("identity_height")) {
            Identity identity = getIdentity(playerUuid);
            if (identity != null) {
                return String.valueOf(identity.getHeight());
            }
            return "";
        }

        // %roleplaycity_identity_residence% → "Paris"
        if (identifier.equals("identity_residence")) {
            Identity identity = getIdentity(playerUuid);
            if (identity != null && identity.getResidenceCity() != null) {
                return identity.getResidenceCity();
            }
            return "";
        }

        // %roleplaycity_identity_has% → "true" ou "false"
        if (identifier.equals("identity_has")) {
            Identity identity = getIdentity(playerUuid);
            return identity != null ? "true" : "false";
        }

        return null;
    }

    /**
     * Récupère l'identité d'un joueur
     */
    private Identity getIdentity(UUID playerUuid) {
        if (plugin.getIdentityManager() != null) {
            return plugin.getIdentityManager().getIdentity(playerUuid);
        }
        return null;
    }

    /**
     * Obtient le grade du joueur dans sa ville selon la priorité définie
     * Priorité : Maire > Juge > Policier > Médecin > Architecte > Entrepreneur > Adjoint
     */
    private String getPlayerRank(UUID playerUuid, boolean colored) {
        String townName = townManager.getPlayerTown(playerUuid);
        if (townName == null) {
            return ""; // Pas dans une ville
        }

        Town town = townManager.getTown(townName);
        if (town == null) {
            return "";
        }

        TownRole role = town.getMemberRole(playerUuid);
        if (role == null) {
            return "";
        }

        // Priorité 1 : Maire
        if (role == TownRole.MAIRE) {
            return colored ? ChatColor.GOLD + "Maire" : "Maire";
        }

        // Priorité 2 : Juge
        if (role == TownRole.JUGE) {
            return colored ? ChatColor.DARK_PURPLE + "Juge" : "Juge";
        }

        // Priorité 3 : Policier
        if (role == TownRole.POLICIER) {
            return colored ? ChatColor.BLUE + "Policier" : "Policier";
        }

        // Priorité 4 : Médecin
        if (role == TownRole.MEDECIN) {
            return colored ? ChatColor.RED + "Médecin" : "Médecin";
        }

        // Priorité 5 : Architecte
        if (role == TownRole.ARCHITECTE) {
            return colored ? ChatColor.GREEN + "Architecte" : "Architecte";
        }

        // Priorité 6 : Entrepreneur (si le joueur possède une entreprise)
        if (hasCompany(playerUuid)) {
            return colored ? ChatColor.DARK_GREEN + "Entrepreneur" : "Entrepreneur";
        }

        // Priorité 7 : Adjoint
        if (role == TownRole.ADJOINT) {
            return colored ? ChatColor.YELLOW + "Adjoint" : "Adjoint";
        }

        // Aucun grade spécial
        return "";
    }

    /**
     * Vérifie si le joueur possède une entreprise
     */
    private boolean hasCompany(UUID playerUuid) {
        // Vérifier si le joueur possède au moins une entreprise via EntrepriseManagerLogic
        if (plugin.getEntrepriseManagerLogic() != null) {
            return plugin.getEntrepriseManagerLogic().getEntreprises().stream()
                .anyMatch(entreprise -> {
                    String gerantUuidStr = entreprise.getGerantUUID();
                    if (gerantUuidStr != null) {
                        try {
                            UUID gerantUuid = java.util.UUID.fromString(gerantUuidStr);
                            return gerantUuid.equals(playerUuid);
                        } catch (IllegalArgumentException e) {
                            return false;
                        }
                    }
                    return false;
                });
        }
        return false;
    }
}
