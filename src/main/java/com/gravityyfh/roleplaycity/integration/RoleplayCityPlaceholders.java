package com.gravityyfh.roleplaycity.integration;

import com.gravityyfh.roleplaycity.RoleplayCity;
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

        return null;
    }

    /**
     * Obtient le grade du joueur dans sa ville selon la priorité définie
     * Priorité : Maire > Juge > Policier > Architecte > Ambulancier > Entrepreneur > rien
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

        // Priorité 4 : Architecte (système viendra plus tard)
        if (role == TownRole.ARCHITECTE) {
            return colored ? ChatColor.GREEN + "Architecte" : "Architecte";
        }

        // Priorité 5 : Ambulancier (système viendra plus tard)
        // Note: TownRole.AMBULANCIER n'existe pas encore
        // if (role == TownRole.AMBULANCIER) {
        //     return colored ? ChatColor.RED + "Ambulancier" : "Ambulancier";
        // }

        // Priorité 6 : Entrepreneur (si le joueur possède une entreprise)
        if (hasCompany(playerUuid)) {
            return colored ? ChatColor.DARK_GREEN + "Entrepreneur" : "Entrepreneur";
        }

        // Priorité 7 : Adjoint (pas dans la liste mais important)
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
                .anyMatch(entreprise -> entreprise.getGerant().equals(playerUuid));
        }
        return false;
    }
}
