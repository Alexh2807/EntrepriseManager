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

    // Niveau d'évolution de la ville (Campement, Village, Ville)
    private TownLevel level;

    // Membres : UUID -> TownMember
    private final Map<UUID, TownMember> members;

    // ⚠️ NOUVEAU SYSTÈME UNIFIÉ : Parcelles (1 ou plusieurs chunks)
    // "world:chunkX:chunkZ" -> Plot (qui peut contenir plusieurs chunks si grouped=true)
    private final Map<String, Plot> plots;

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

        // Initialiser au niveau CAMPEMENT par défaut
        this.level = TownLevel.CAMPEMENT;

        this.members = new ConcurrentHashMap<>();
        this.plots = new ConcurrentHashMap<>();
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
    public TownLevel getLevel() { return level; }
    public Map<UUID, TownMember> getMembers() { return new HashMap<>(members); }
    public Map<String, Plot> getPlots() { return new HashMap<>(plots); }
    public int getTotalClaims() { return totalClaims; }
    public LocalDateTime getLastTaxCollection() { return lastTaxCollection; }

    // Setters
    public void setDescription(String description) { this.description = description; }
    public void setCitizenTax(double tax) { this.citizenTax = tax; }
    public void setCompanyTax(double tax) { this.companyTax = tax; }
    public void setLastTaxCollection(LocalDateTime time) { this.lastTaxCollection = time; }
    public void setLevel(TownLevel level) { this.level = level; }

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
        // Ne jamais retirer le maire sans transfert explicite
        if (mayorUuid.equals(playerUuid)) {
            throw new IllegalArgumentException("Le maire ne peut pas être retiré de la ville sans transfert.");
        }

        members.remove(playerUuid);
        // Retirer également les parcelles du joueur
        plots.values().stream()
            .filter(plot -> plot.isOwnedBy(playerUuid))
            .forEach(Plot::clearOwner);
    }

    public void setMemberRole(UUID playerUuid, TownRole role) {
        // Le rôle du maire est fixe
        if (mayorUuid.equals(playerUuid) && role != TownRole.MAIRE) {
            throw new IllegalArgumentException("Le rôle du maire ne peut pas être modifié.");
        }

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

    // Surcharge pour charger depuis la persistance avec une date spécifique
    public void invitePlayer(UUID playerUuid, LocalDateTime inviteDate) {
        pendingInvitations.put(playerUuid, inviteDate);
    }

    public void cancelInvitation(UUID playerUuid) {
        pendingInvitations.remove(playerUuid);
    }

    public boolean hasInvitation(UUID playerUuid) {
        return pendingInvitations.containsKey(playerUuid);
    }

    public Map<UUID, LocalDateTime> getPendingInvitations() {
        return new HashMap<>(pendingInvitations);
    }

    public void clearExpiredInvitations(int expirationHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(expirationHours);
        pendingInvitations.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    // === GESTION DES PARCELLES ===

    public void addPlot(Plot plot) {
        String key = getPlotKey(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
        Plot previous = plots.put(key, plot);
        if (previous == null) {
            totalClaims++;
        }
    }

    public void removePlot(String worldName, int chunkX, int chunkZ) {
        String key = getPlotKey(worldName, chunkX, chunkZ);
        Plot removedPlot = plots.remove(key);
        if (removedPlot != null) {
            totalClaims--;
        }
    }

    public Plot getPlot(String worldName, int chunkX, int chunkZ) {
        String chunkKey = getPlotKey(worldName, chunkX, chunkZ);

        // D'abord, chercher par clé directe (optimisation pour plots individuels)
        Plot directPlot = plots.get(chunkKey);
        if (directPlot != null) {
            return directPlot;
        }

        // Si pas trouvé, chercher dans TOUS les chunks de TOUS les plots (pour plots groupés)
        for (Plot plot : plots.values()) {
            if (plot.containsChunk(worldName, chunkX, chunkZ)) {
                return plot;
            }
        }

        return null;
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

    /**
     * Génère un numéro unique de terrain pour cette ville
     * Format: [Première lettre de la ville]-[001-999]
     * Exemple: V-001 pour Veloria
     * Retourne null si tous les numéros sont utilisés
     */
    public String generateUniquePlotNumber() {
        // Extraire la première lettre du nom de la ville
        String prefix = name.substring(0, 1).toUpperCase();

        // Collecter tous les numéros actuellement utilisés
        Set<Integer> usedNumbers = new HashSet<>();
        for (Plot plot : plots.values()) {
            String plotNumber = plot.getPlotNumber();
            if (plotNumber != null && plotNumber.startsWith(prefix + "-")) {
                try {
                    int number = Integer.parseInt(plotNumber.substring(prefix.length() + 1));
                    usedNumbers.add(number);
                } catch (NumberFormatException ignored) {
                    // Ignorer les formats invalides
                }
            }
        }

        // Trouver le plus petit numéro disponible de 1 à 999
        for (int i = 1; i <= 999; i++) {
            if (!usedNumbers.contains(i)) {
                return String.format("%s-%03d", prefix, i);
            }
        }

        // Tous les numéros sont utilisés
        return null;
    }

    /**
     * Calcule le nombre réel de chunks claimés par la ville
     * (en additionnant tous les chunks de tous les plots)
     */
    public int getRealChunkCount() {
        int total = 0;
        // Utiliser un Set pour éviter de compter plusieurs fois le même chunk
        Set<String> countedChunks = new HashSet<>();

        for (Plot plot : plots.values()) {
            for (String chunkKey : plot.getChunks()) {
                countedChunks.add(chunkKey);
            }
        }

        return countedChunks.size();
    }


    // === GESTION ÉCONOMIQUE ===

    public void deposit(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Le montant déposé doit être positif.");
        }
        bankBalance += amount;
    }

    public boolean withdraw(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Le montant à retirer doit être positif.");
        }
        if (bankBalance >= amount) {
            bankBalance -= amount;
            return true;
        }
        return false;
    }

    public double calculateTotalTaxes() {
        double total = 0.0;
        // Taxes des citoyens (uniquement si taxe positive)
        if (citizenTax > 0) {
            total += members.size() * citizenTax;
        }
        // Taxes des parcelles
        for (Plot plot : plots.values()) {
            total += plot.getDailyTax();
            if (companyTax > 0 && plot.getType() == PlotType.PROFESSIONNEL) {
                total += companyTax;
            }
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

    // === NOUVEAU : GESTION DES DETTES ===

    /**
     * Classe pour représenter une dette d'un joueur
     */
    public static class PlayerDebt {
        private final Plot plot;
        private final double amount;
        private final LocalDateTime warningDate;

        public PlayerDebt(Plot plot, double amount, LocalDateTime warningDate) {
            this.plot = plot;
            this.amount = amount;
            this.warningDate = warningDate;
        }

        public Plot getPlot() { return plot; }
        public double getAmount() { return amount; }
        public LocalDateTime getWarningDate() { return warningDate; }
    }

    /**
     * Récupère toutes les dettes d'un joueur (particulier + entreprise)
     */
    public List<PlayerDebt> getPlayerDebts(UUID playerUuid) {
        List<PlayerDebt> debts = new ArrayList<>();

        // Parcourir toutes les parcelles
        for (Plot plot : plots.values()) {
            // Vérifier si le joueur est propriétaire ou locataire
            boolean isOwner = plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(playerUuid);
            boolean isRenter = plot.getRenterUuid() != null && plot.getRenterUuid().equals(playerUuid);

            if (isOwner || isRenter) {
                // Une parcelle ne doit avoir qu'UNE seule dette (entreprise OU particulier, pas les deux)
                // Prioriser la dette entreprise si elle existe
                if (plot.getCompanyDebtAmount() > 0) {
                    debts.add(new PlayerDebt(
                        plot,
                        plot.getCompanyDebtAmount(),
                        plot.getLastDebtWarningDate()
                    ));
                } else if (plot.getParticularDebtAmount() > 0) {
                    debts.add(new PlayerDebt(
                        plot,
                        plot.getParticularDebtAmount(),
                        plot.getParticularLastDebtWarningDate()
                    ));
                }
            }
        }

        return debts;
    }

    /**
     * Calcule le montant total des dettes d'un joueur
     */
    public double getTotalPlayerDebt(UUID playerUuid) {
        return getPlayerDebts(playerUuid).stream()
            .mapToDouble(PlayerDebt::getAmount)
            .sum();
    }

    /**
     * Vérifie si un joueur a des dettes
     */
    public boolean hasPlayerDebts(UUID playerUuid) {
        return !getPlayerDebts(playerUuid).isEmpty();
    }

    @Override
    public String toString() {
        return String.format("Town{name='%s', members=%d, claims=%d, balance=%.2f€}",
            name, members.size(), totalClaims, bankBalance);
    }
}
