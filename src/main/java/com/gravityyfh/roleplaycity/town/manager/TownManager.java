package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownMember;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.event.TownDeleteEvent;
import com.gravityyfh.roleplaycity.town.event.TownMemberLeaveEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TownManager {
    private final RoleplayCity plugin;
    private final Map<String, Town> towns; // townName -> Town
    private final Map<UUID, String> playerTowns; // playerUUID -> townName

    public TownManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.towns = new ConcurrentHashMap<>();
        this.playerTowns = new ConcurrentHashMap<>();
    }

    // === CRÉATION ET SUPPRESSION ===

    public boolean createTown(String townName, Player mayor, double creationCost) {
        // Vérifications
        if (townExists(townName)) {
            return false;
        }

        if (getPlayerTown(mayor.getUniqueId()) != null) {
            return false; // Déjà dans une ville
        }

        // Vérifier l'économie
        if (!RoleplayCity.getEconomy().has(mayor, creationCost)) {
            return false;
        }

        // Prélever le coût
        RoleplayCity.getEconomy().withdrawPlayer(mayor, creationCost);

        // Créer la ville
        Town town = new Town(townName, mayor.getUniqueId(), mayor.getName());
        towns.put(townName, town);
        playerTowns.put(mayor.getUniqueId(), townName);

        plugin.getLogger().info("Ville créée: " + townName + " par " + mayor.getName());
        return true;
    }

    public boolean deleteTown(String townName) {
        Town town = towns.remove(townName);
        if (town == null) {
            return false;
        }

        // Déclencher l'événement de suppression de ville
        TownDeleteEvent event = new TownDeleteEvent(townName);
        Bukkit.getPluginManager().callEvent(event);

        // Retirer tous les membres
        for (UUID memberUuid : town.getMembers().keySet()) {
            playerTowns.remove(memberUuid);
        }

        plugin.getLogger().info("Ville supprimée: " + townName);
        return true;
    }

    public boolean renameTown(String oldName, String newName, double renameCost) {
        if (!townExists(oldName) || townExists(newName)) {
            return false;
        }

        Town town = towns.get(oldName);

        // Vérifier le solde de la ville
        if (town.getBankBalance() < renameCost) {
            return false;
        }

        // Prélever le coût
        town.withdraw(renameCost);

        // Renommer (créer nouvelle entrée et supprimer l'ancienne)
        towns.remove(oldName);
        towns.put(newName, town);

        // Mettre à jour les références des joueurs
        for (UUID memberUuid : town.getMembers().keySet()) {
            playerTowns.put(memberUuid, newName);
        }

        plugin.getLogger().info("Ville renommée: " + oldName + " -> " + newName);
        return true;
    }

    // === GESTION DES MEMBRES ===

    public boolean invitePlayer(String townName, Player inviter, Player invited) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions
        if (!town.isMember(inviter.getUniqueId())) {
            return false;
        }

        TownRole inviterRole = town.getMemberRole(inviter.getUniqueId());
        if (inviterRole != TownRole.MAIRE && inviterRole != TownRole.ADJOINT) {
            return false;
        }

        // Vérifier que le joueur invité n'est pas déjà dans une ville
        if (getPlayerTown(invited.getUniqueId()) != null) {
            return false;
        }

        // Ajouter l'invitation
        town.invitePlayer(invited.getUniqueId());
        return true;
    }

    public boolean acceptInvitation(Player player, String townName) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier l'invitation
        if (!town.hasInvitation(player.getUniqueId())) {
            return false;
        }

        // Vérifier que le joueur n'est pas déjà dans une ville
        if (getPlayerTown(player.getUniqueId()) != null) {
            return false;
        }

        // Ajouter le joueur
        town.addMember(player.getUniqueId(), player.getName(), TownRole.CITOYEN);
        town.cancelInvitation(player.getUniqueId());
        playerTowns.put(player.getUniqueId(), townName);

        return true;
    }

    public boolean refuseInvitation(Player player, String townName) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        town.cancelInvitation(player.getUniqueId());
        return true;
    }

    public boolean joinTown(Player player, String townName, double joinCost) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier que le joueur n'est pas déjà dans une ville
        if (getPlayerTown(player.getUniqueId()) != null) {
            return false;
        }

        // Vérifier l'économie
        if (!RoleplayCity.getEconomy().has(player, joinCost)) {
            return false;
        }

        // Prélever le coût
        RoleplayCity.getEconomy().withdrawPlayer(player, joinCost);

        // Ajouter le joueur
        town.addMember(player.getUniqueId(), player.getName(), TownRole.CITOYEN);
        playerTowns.put(player.getUniqueId(), townName);

        // Déposer le coût dans la banque de la ville
        town.deposit(joinCost);

        return true;
    }

    public boolean leaveTown(Player player) {
        String townName = getPlayerTown(player.getUniqueId());
        if (townName == null) {
            return false;
        }

        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Le maire ne peut pas quitter (il doit supprimer la ville ou transférer)
        if (town.isMayor(player.getUniqueId())) {
            return false;
        }

        // Déclencher l'événement de départ de membre
        TownMemberLeaveEvent event = new TownMemberLeaveEvent(townName, player.getUniqueId(), player.getName());
        Bukkit.getPluginManager().callEvent(event);

        // Retirer le joueur
        town.removeMember(player.getUniqueId());
        playerTowns.remove(player.getUniqueId());

        return true;
    }

    public boolean kickMember(String townName, Player kicker, UUID kickedUuid) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions
        TownRole kickerRole = town.getMemberRole(kicker.getUniqueId());
        if (kickerRole != TownRole.MAIRE && kickerRole != TownRole.ADJOINT) {
            return false;
        }

        // On ne peut pas kick le maire
        if (town.isMayor(kickedUuid)) {
            return false;
        }

        // Vérifier le pouvoir
        TownRole kickedRole = town.getMemberRole(kickedUuid);
        if (kickedRole != null && kickedRole.getPower() >= kickerRole.getPower()) {
            return false; // Ne peut pas kick quelqu'un de rang égal ou supérieur
        }

        // Récupérer le nom du joueur expulsé avant de le retirer
        TownMember kickedMember = town.getMember(kickedUuid);
        String kickedName = kickedMember != null ? kickedMember.getPlayerName() : kickedUuid.toString();

        // Déclencher l'événement de départ de membre
        TownMemberLeaveEvent event = new TownMemberLeaveEvent(townName, kickedUuid, kickedName);
        Bukkit.getPluginManager().callEvent(event);

        // Retirer le joueur
        town.removeMember(kickedUuid);
        playerTowns.remove(kickedUuid);

        return true;
    }

    public boolean setMemberRole(String townName, Player changer, UUID targetUuid, TownRole newRole) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Seul le maire peut changer les rôles
        if (!town.isMayor(changer.getUniqueId())) {
            return false;
        }

        // On ne peut pas changer le rôle du maire
        if (town.isMayor(targetUuid)) {
            return false;
        }

        // Changer le rôle
        town.setMemberRole(targetUuid, newRole);
        return true;
    }

    // === GESTION DES CLAIMS ===

    public boolean claimChunk(String townName, org.bukkit.Chunk chunk, Player claimer) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions
        TownRole role = town.getMemberRole(claimer.getUniqueId());
        if (role == null || (!role.canManageClaims() && role != TownRole.MAIRE)) {
            return false;
        }

        // Le coût et la logique de claim sont gérés par ClaimManager
        return true;
    }

    public boolean unclaimChunk(String townName, org.bukkit.Chunk chunk, Player claimer) {
        Town town = getTown(townName);
        if (town == null) {
            return false;
        }

        // Vérifier les permissions
        TownRole role = town.getMemberRole(claimer.getUniqueId());
        if (role == null || (!role.canManageClaims() && role != TownRole.MAIRE)) {
            return false;
        }

        return true;
    }

    // === GETTERS ===

    public Town getTown(String townName) {
        return towns.get(townName);
    }

    public String getPlayerTown(UUID playerUuid) {
        return playerTowns.get(playerUuid);
    }

    public Town getPlayerTownObject(UUID playerUuid) {
        String townName = getPlayerTown(playerUuid);
        return townName != null ? getTown(townName) : null;
    }

    public boolean townExists(String townName) {
        return towns.containsKey(townName);
    }

    public Collection<Town> getAllTowns() {
        return new ArrayList<>(towns.values());
    }

    public List<String> getTownNames() {
        return new ArrayList<>(towns.keySet());
    }

    public int getTownCount() {
        return towns.size();
    }

    // === UTILITAIRES ===

    public boolean isPlayerInTown(UUID playerUuid) {
        return playerTowns.containsKey(playerUuid);
    }

    public boolean isPlayerMayor(UUID playerUuid) {
        Town town = getPlayerTownObject(playerUuid);
        return town != null && town.isMayor(playerUuid);
    }

    public TownRole getPlayerRole(UUID playerUuid) {
        Town town = getPlayerTownObject(playerUuid);
        return town != null ? town.getMemberRole(playerUuid) : null;
    }

    public void cleanupExpiredInvitations() {
        int expirationHours = plugin.getConfig().getInt("town.invitation-expiration-hours", 24);
        for (Town town : towns.values()) {
            town.clearExpiredInvitations(expirationHours);
        }
    }

    // === CHARGEMENT/SAUVEGARDE ===

    public void loadTowns(Map<String, Town> loadedTowns) {
        towns.clear();
        playerTowns.clear();
        towns.putAll(loadedTowns);

        // Reconstruire l'index playerTowns
        for (Map.Entry<String, Town> entry : towns.entrySet()) {
            String townName = entry.getKey();
            Town town = entry.getValue();
            for (UUID memberUuid : town.getMembers().keySet()) {
                playerTowns.put(memberUuid, townName);
            }
        }

        plugin.getLogger().info("Chargé " + towns.size() + " villes avec " + playerTowns.size() + " citoyens.");
    }

    public Map<String, Town> getTownsForSave() {
        return new HashMap<>(towns);
    }

    // === MÉTHODES UTILITAIRES POUR TERRAINS D'ENTREPRISE ===

    /**
     * Récupère tous les terrains appartenant à une entreprise (via SIRET)
     */
    public List<com.gravityyfh.roleplaycity.town.data.Plot> getPlotsByCompanySiret(String siret, String townName) {
        List<com.gravityyfh.roleplaycity.town.data.Plot> companyPlots = new ArrayList<>();
        Town town = getTown(townName);

        if (town == null || siret == null) {
            return companyPlots;
        }

        for (com.gravityyfh.roleplaycity.town.data.Plot plot : town.getPlots().values()) {
            if (siret.equals(plot.getCompanySiret())) {
                companyPlots.add(plot);
            }
        }

        return companyPlots;
    }

    /**
     * Retourne un terrain à la ville (efface propriétaire et entreprise)
     */
    public void transferPlotToTown(com.gravityyfh.roleplaycity.town.data.Plot plot, String reason) {
        if (plot == null) {
            return;
        }

        // Sauvegarder les informations pour le log
        String previousOwner = plot.getOwnerName();
        String previousCompany = plot.getCompanyName();

        // Effacer la propriété
        clearPlotOwnership(plot);

        // Annuler vente/location
        plot.setForSale(false);
        plot.setForRent(false);
        plot.setSalePrice(0);
        plot.setRentPricePerDay(0);

        // Effacer le locataire
        plot.clearRenter();

        // Retirer du groupe si nécessaire
        Town town = getTown(plot.getTownName());
        if (town != null) {
            town.removePlotFromGroupIfIncompatible(plot);
        }

        plugin.getLogger().info(String.format(
            "[TownManager] Terrain %s:%d,%d retourné à la ville. Raison: %s. Ancien propriétaire: %s, Entreprise: %s",
            plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), reason,
            previousOwner != null ? previousOwner : "Aucun",
            previousCompany != null ? previousCompany : "Aucune"
        ));
    }

    /**
     * Efface la propriété d'un terrain (propriétaire, entreprise, dette)
     */
    public void clearPlotOwnership(com.gravityyfh.roleplaycity.town.data.Plot plot) {
        if (plot == null) {
            return;
        }

        plot.setOwner(null, null);
        plot.setCompany(null);
        plot.setCompanySiret(null);
        plot.resetDebt();
    }
}
