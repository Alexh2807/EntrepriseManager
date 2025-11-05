package com.gravityyfh.roleplaycity.town.data;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Town {
    private final String name;
    private String description;
    private final UUID mayorUuid;
    private final LocalDateTime foundationDate;

    private double bankBalance;
    private double citizenTax;
    private double companyTax;

    // Membres : UUID -> TownMember
    private final Map<UUID, TownMember> members;

    // Parcelles : "world:chunkX:chunkZ" -> Plot
    private final Map<String, Plot> plots;

    // Groupes de parcelles : groupId -> PlotGroup
    private final Map<String, PlotGroup> plotGroups;

    // Invitations en attente : UUID du joueur invité -> timestamp
    private final Map<UUID, LocalDateTime> pendingInvitations;

    // Statistiques
    private int totalClaims;
    private LocalDateTime lastTaxCollection;

    public Town(String name, UUID mayorUuid, String mayorName) {
        this.name = name;
        this.description = "Bienvenue à " + name;
        this.mayorUuid = mayorUuid;
        this.foundationDate = LocalDateTime.now();

        this.bankBalance = 0.0;
        this.citizenTax = 0.0;
        this.companyTax = 0.0;

        this.members = new ConcurrentHashMap<>();
        this.plots = new ConcurrentHashMap<>();
        this.plotGroups = new ConcurrentHashMap<>();
        this.pendingInvitations = new ConcurrentHashMap<>();

        // Ajouter le maire comme premier membre
        this.members.put(mayorUuid, new TownMember(mayorUuid, mayorName, TownRole.MAIRE));

        this.totalClaims = 0;
        this.lastTaxCollection = LocalDateTime.now();
    }

    // Getters de base
    public String getName() { return name; }
    public String getDescription() { return description; }
    public UUID getMayorUuid() { return mayorUuid; }
    public LocalDateTime getFoundationDate() { return foundationDate; }
    public double getBankBalance() { return bankBalance; }
    public double getCitizenTax() { return citizenTax; }
    public double getCompanyTax() { return companyTax; }
    public Map<UUID, TownMember> getMembers() { return new HashMap<>(members); }
    public Map<String, Plot> getPlots() { return new HashMap<>(plots); }
    public int getTotalClaims() { return totalClaims; }
    public LocalDateTime getLastTaxCollection() { return lastTaxCollection; }

    // Setters
    public void setDescription(String description) { this.description = description; }
    public void setCitizenTax(double tax) { this.citizenTax = tax; }
    public void setCompanyTax(double tax) { this.companyTax = tax; }
    public void setLastTaxCollection(LocalDateTime time) { this.lastTaxCollection = time; }

    // === GESTION DES MEMBRES ===

    public boolean isMember(UUID playerUuid) {
        return members.containsKey(playerUuid);
    }

    public TownMember getMember(UUID playerUuid) {
        return members.get(playerUuid);
    }

    public TownRole getMemberRole(UUID playerUuid) {
        TownMember member = members.get(playerUuid);
        return member != null ? member.getRole() : null;
    }

    public boolean isMayor(UUID playerUuid) {
        return mayorUuid.equals(playerUuid);
    }

    public void addMember(UUID playerUuid, String playerName, TownRole role) {
        members.put(playerUuid, new TownMember(playerUuid, playerName, role));
    }

    public void removeMember(UUID playerUuid) {
        members.remove(playerUuid);
        // Retirer également les parcelles du joueur
        plots.values().stream()
            .filter(plot -> plot.isOwnedBy(playerUuid))
            .forEach(Plot::clearOwner);
    }

    public void setMemberRole(UUID playerUuid, TownRole role) {
        TownMember member = members.get(playerUuid);
        if (member != null) {
            member.setRole(role);
        }
    }

    public List<TownMember> getMembersByRole(TownRole role) {
        return members.values().stream()
            .filter(m -> m.getRole() == role)
            .toList();
    }

    public int getMemberCount() {
        return members.size();
    }

    // === GESTION DES INVITATIONS ===

    public void invitePlayer(UUID playerUuid) {
        pendingInvitations.put(playerUuid, LocalDateTime.now());
    }

    public void cancelInvitation(UUID playerUuid) {
        pendingInvitations.remove(playerUuid);
    }

    public boolean hasInvitation(UUID playerUuid) {
        return pendingInvitations.containsKey(playerUuid);
    }

    public void clearExpiredInvitations(int expirationHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(expirationHours);
        pendingInvitations.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    // === GESTION DES PARCELLES ===

    public void addPlot(Plot plot) {
        String key = getPlotKey(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
        plots.put(key, plot);
        totalClaims++;
    }

    public void removePlot(String worldName, int chunkX, int chunkZ) {
        String key = getPlotKey(worldName, chunkX, chunkZ);
        if (plots.remove(key) != null) {
            totalClaims--;
        }
    }

    public Plot getPlot(String worldName, int chunkX, int chunkZ) {
        String key = getPlotKey(worldName, chunkX, chunkZ);
        return plots.get(key);
    }

    public boolean hasPlot(String worldName, int chunkX, int chunkZ) {
        return getPlot(worldName, chunkX, chunkZ) != null;
    }

    public List<Plot> getPlotsByType(PlotType type) {
        return plots.values().stream()
            .filter(plot -> plot.getType() == type)
            .toList();
    }

    public List<Plot> getPlotsByOwner(UUID ownerUuid) {
        return plots.values().stream()
            .filter(plot -> plot.isOwnedBy(ownerUuid))
            .toList();
    }

    private String getPlotKey(String world, int x, int z) {
        return world + ":" + x + ":" + z;
    }

    // === GESTION DES GROUPES DE PARCELLES ===

    public PlotGroup createPlotGroup(String groupName) {
        String groupId = UUID.randomUUID().toString();
        PlotGroup group = new PlotGroup(groupId, this.name);
        group.setGroupName(groupName);
        plotGroups.put(groupId, group);
        return group;
    }

    public void removePlotGroup(String groupId) {
        plotGroups.remove(groupId);
    }

    public PlotGroup getPlotGroup(String groupId) {
        return plotGroups.get(groupId);
    }

    public Map<String, PlotGroup> getPlotGroups() {
        return new HashMap<>(plotGroups);
    }

    /**
     * Retourne tous les groupes de parcelles appartenant à un joueur spécifique
     */
    public List<PlotGroup> getPlayerOwnedGroups(UUID playerUuid) {
        List<PlotGroup> ownedGroups = new ArrayList<>();
        for (PlotGroup group : plotGroups.values()) {
            if (group.isOwnedBy(playerUuid)) {
                ownedGroups.add(group);
            }
        }
        return ownedGroups;
    }

    public PlotGroup findPlotGroupByPlot(Plot plot) {
        return plotGroups.values().stream()
            .filter(group -> group.containsPlot(plot))
            .findFirst()
            .orElse(null);
    }

    public boolean isPlotInAnyGroup(Plot plot) {
        return findPlotGroupByPlot(plot) != null;
    }

    public void addPlotGroup(PlotGroup group) {
        plotGroups.put(group.getGroupId(), group);
    }

    /**
     * Retire une parcelle de son groupe si le type n'est plus compatible
     * Retourne true si la parcelle a été retirée d'un groupe
     */
    public boolean removePlotFromGroupIfIncompatible(Plot plot) {
        PlotGroup group = findPlotGroupByPlot(plot);
        if (group == null) {
            return false; // Pas dans un groupe
        }

        // Vérifier si le type est compatible (PARTICULIER ou PROFESSIONNEL uniquement)
        if (plot.getType() != PlotType.PARTICULIER && plot.getType() != PlotType.PROFESSIONNEL) {
            // Retirer la parcelle du groupe
            group.removePlot(plot);

            // Annuler vente/location du groupe si moins de 2 parcelles
            if (group.getPlotCount() < 2) {
                group.setForSale(false);
                group.setForRent(false);
                group.clearRenter();
                // Supprimer le groupe complètement
                plotGroups.remove(group.getGroupId());
            }

            return true;
        }

        return false;
    }

    // === GESTION ÉCONOMIQUE ===

    public void deposit(double amount) {
        bankBalance += amount;
    }

    public boolean withdraw(double amount) {
        if (bankBalance >= amount) {
            bankBalance -= amount;
            return true;
        }
        return false;
    }

    public double calculateTotalTaxes() {
        double total = 0.0;
        // Taxes des citoyens
        total += members.size() * citizenTax;
        // Taxes des parcelles
        for (Plot plot : plots.values()) {
            total += plot.getDailyTax();
        }
        return total;
    }

    public double calculateHourlyTaxes() {
        return calculateTotalTaxes() / 24.0;
    }

    // === STATISTIQUES ===

    public Map<PlotType, Long> getPlotStatistics() {
        Map<PlotType, Long> stats = new HashMap<>();
        for (PlotType type : PlotType.values()) {
            long count = plots.values().stream()
                .filter(plot -> plot.getType() == type)
                .count();
            stats.put(type, count);
        }
        return stats;
    }

    public Map<TownRole, Long> getRoleStatistics() {
        Map<TownRole, Long> stats = new HashMap<>();
        for (TownRole role : TownRole.values()) {
            long count = members.values().stream()
                .filter(member -> member.getRole() == role)
                .count();
            stats.put(role, count);
        }
        return stats;
    }

    @Override
    public String toString() {
        return String.format("Town{name='%s', members=%d, claims=%d, balance=%.2f€}",
            name, members.size(), totalClaims, bankBalance);
    }
}
