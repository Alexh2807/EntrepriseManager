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

            // FIX CRITIQUE: Retirer la parcelle de tous les groupes
            PlotGroup group = findPlotGroupByPlot(removedPlot);
            if (group != null) {
                group.removePlot(removedPlot);

                // Si le groupe a moins de 2 parcelles, le supprimer complètement
                if (group.getChunkKeys().size() < 2) {
                    plotGroups.remove(group.getGroupId());
                }
            }
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

    /**
     * @deprecated Utilisez {@link #getTerritoryAt(String, int, int)} à la place.
     * Cette méthode perpétue l'ancien modèle où plots et groupes coexistent.
     * Dans le nouveau système autonome, un chunk est SOIT un Plot SOIT dans un PlotGroup.
     *
     * <p>Exemple de remplacement:</p>
     * <pre>
     * // AVANT
     * PlotGroup group = town.findPlotGroupByPlot(plot);
     *
     * // APRÈS
     * TerritoryEntity territory = town.getTerritoryAt(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
     * PlotGroup group = (territory instanceof PlotGroup) ? (PlotGroup) territory : null;
     * </pre>
     */
    @Deprecated
    public PlotGroup findPlotGroupByPlot(Plot plot) {
        return plotGroups.values().stream()
            .filter(group -> group.containsPlot(plot))
            .findFirst()
            .orElse(null);
    }

    /**
     * @deprecated Utilisez {@link #getTerritoryAt(String, int, int)} avec {@code instanceof PlotGroup} à la place.
     * Cette méthode est obsolète car les plots groupés n'existent plus en tant qu'entités séparées.
     *
     * <p>Exemple de remplacement:</p>
     * <pre>
     * // AVANT
     * if (town.isPlotInAnyGroup(plot)) { ... }
     *
     * // APRÈS
     * TerritoryEntity territory = town.getTerritoryAt(plot.getWorldName(), plot.getChunkX(), plot.getChunkZ());
     * if (territory instanceof PlotGroup) { ... }
     * </pre>
     */
    @Deprecated
    public boolean isPlotInAnyGroup(Plot plot) {
        return findPlotGroupByPlot(plot) != null;
    }

    public void addPlotGroup(PlotGroup group) {
        plotGroups.put(group.getGroupId(), group);
    }

    /**
     * FIX CRITIQUE: Retire une parcelle de son groupe (peu importe le type)
     * Utilisé lors de saisie, vente, transfert à la ville
     * Retourne true si la parcelle a été retirée d'un groupe
     */
    public boolean removePlotFromGroup(Plot plot) {
        PlotGroup group = findPlotGroupByPlot(plot);
        if (group == null) {
            return false; // Pas dans un groupe
        }

        // Retirer la parcelle du groupe
        group.removePlot(plot);

        // Annuler vente/location du groupe si moins de 2 parcelles
        if (group.getChunkKeys().size() < 2) {
            group.setForSale(false);
            group.setForRent(false);
            group.clearRenter();
            // Supprimer le groupe complètement
            plotGroups.remove(group.getGroupId());
        }

        return true;
    }

    // ========== NOUVEAU SYSTÈME : PlotGroup Autonome ==========

    /**
     * Trouve le PlotGroup contenant une position géographique donnée
     * @param location La position à vérifier
     * @return Le PlotGroup si trouvé, null sinon
     */
    public PlotGroup getPlotGroupAt(org.bukkit.Location location) {
        if (location == null) return null;

        org.bukkit.Chunk chunk = location.getChunk();
        String chunkKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();

        for (PlotGroup group : plotGroups.values()) {
            if (group.containsChunk(chunkKey)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Trouve le PlotGroup contenant une coordonnée chunk spécifique
     * @param world Nom du monde
     * @param chunkX Coordonnée X du chunk
     * @param chunkZ Coordonnée Z du chunk
     * @return Le PlotGroup si trouvé, null sinon
     */
    public PlotGroup getPlotGroupAt(String world, int chunkX, int chunkZ) {
        String chunkKey = world + ":" + chunkX + ":" + chunkZ;

        for (PlotGroup group : plotGroups.values()) {
            if (group.containsChunk(chunkKey)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Interface unifiée pour récupérer une entité territoriale (Plot OU PlotGroup)
     * à une position donnée.
     *
     * PRIORITÉ : PlotGroup > Plot
     * Si un PlotGroup existe à cette position, il est retourné (les plots individuels n'existent plus)
     * Sinon, cherche un Plot individuel
     *
     * @param location La position à vérifier
     * @return L'entité territoriale (Plot ou PlotGroup), ou null
     */
    public TerritoryEntity getTerritoryAt(org.bukkit.Location location) {
        if (location == null) return null;

        // 1. Priorité : chercher un PlotGroup (entité autonome)
        PlotGroup group = getPlotGroupAt(location);
        if (group != null) {
            return group;
        }

        // 2. Sinon, chercher un Plot individuel (non groupé)
        org.bukkit.Chunk chunk = location.getChunk();
        String plotKey = chunk.getWorld().getName() + ":" + chunk.getX() + ":" + chunk.getZ();
        return plots.get(plotKey);
    }

    /**
     * Interface unifiée par coordonnées
     */
    public TerritoryEntity getTerritoryAt(String world, int chunkX, int chunkZ) {
        // 1. Priorité : PlotGroup
        PlotGroup group = getPlotGroupAt(world, chunkX, chunkZ);
        if (group != null) {
            return group;
        }

        // 2. Sinon : Plot individuel
        String plotKey = world + ":" + chunkX + ":" + chunkZ;
        return plots.get(plotKey);
    }

    /**
     * Dissout un groupe et recrée les plots individuels à partir de ses propriétés
     *
     * IMPORTANT : Cette méthode recrée des plots individuels avec les propriétés du groupe
     * réparties équitablement (ex: dette divisée par nombre de chunks)
     *
     * @param groupId L'ID du groupe à dissoudre
     * @return Liste des plots créés, ou liste vide si échec
     */
    public List<Plot> dissolveGroup(String groupId) {
        PlotGroup group = plotGroups.get(groupId);
        if (group == null) {
            return new ArrayList<>();
        }

        List<Plot> createdPlots = new ArrayList<>();
        Set<String> chunks = group.getChunkKeys();

        // Calculer la dette par plot
        double companyDebtPerPlot = group.getCompanyDebtAmount() / chunks.size();
        double particularDebtPerPlot = group.getParticularDebtAmount() / chunks.size();

        // Créer un plot pour chaque chunk
        for (String chunkKey : chunks) {
            String[] parts = chunkKey.split(":");
            if (parts.length != 3) {
                continue;
            }

            String world = parts[0];
            int chunkX = Integer.parseInt(parts[1]);
            int chunkZ = Integer.parseInt(parts[2]);

            // Créer le plot via Bukkit (nécessite le monde chargé)
            org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(world);
            if (bukkitWorld == null) {
                continue; // Monde non chargé, skip
            }

            org.bukkit.Chunk chunk = bukkitWorld.getChunkAt(chunkX, chunkZ);
            Plot plot = new Plot(name, chunk);

            // === COPIER TOUTES LES PROPRIÉTÉS DU GROUPE ===

            // Type et propriétaire
            plot.setType(group.getType());
            plot.setMunicipalSubType(group.getMunicipalSubType());
            plot.setOwner(group.getOwnerUuid(), group.getOwnerName());
            if (group.getCompanyName() != null) {
                plot.setCompany(group.getCompanyName());
            }
            if (group.getCompanySiret() != null) {
                plot.setCompanySiret(group.getCompanySiret());
            }
            plot.setClaimDate(group.getClaimDate());

            // Dettes (réparties)
            plot.setCompanyDebtAmount(companyDebtPerPlot);
            plot.setParticularDebtAmount(particularDebtPerPlot);
            plot.setDebtWarningCount(group.getDebtWarningCount());
            plot.setParticularDebtWarningCount(group.getParticularDebtWarningCount());
            plot.setLastDebtWarningDate(group.getLastDebtWarningDate());
            plot.setParticularLastDebtWarningDate(group.getParticularLastDebtWarningDate());

            // Vente/Location (même prix pour chaque plot)C'e
            plot.setForSale(group.isForSale());
            plot.setSalePrice(group.getSalePrice());
            plot.setForRent(group.isForRent());
            plot.setRentPricePerDay(group.getRentPricePerDay());

            // Locataire actuel (copié sur tous les plots)
            if (group.getRenterUuid() != null) {
                plot.setRenter(group.getRenterUuid(), group.getRentDaysRemaining());
                plot.setRenterCompanySiret(group.getRenterCompanySiret());
            }

            // Permissions (copiées sur tous les plots)
            for (Map.Entry<UUID, Set<PlotPermission>> entry : group.getAllPlayerPermissions().entrySet()) {
                plot.setPlayerPermissions(entry.getKey(), entry.getValue());
            }

            // Trusted players (copiés)
            for (UUID trusted : group.getTrustedPlayers()) {
                plot.addTrustedPlayer(trusted);
            }

            // Flags (copiés)
            for (Map.Entry<PlotFlag, Boolean> entry : group.getAllFlags().entrySet()) {
                plot.setFlag(entry.getKey(), entry.getValue());
            }

            // Ajouter le plot à la ville
            plots.put(chunkKey, plot);
            createdPlots.add(plot);
        }

        // Supprimer le groupe
        plotGroups.remove(groupId);

        return createdPlots;
    }

    /**
     * Vérifie si un chunk donné fait partie d'un PlotGroup
     * @return true si le chunk est dans un groupe, false sinon
     */
    public boolean isChunkInAnyGroup(String world, int chunkX, int chunkZ) {
        return getPlotGroupAt(world, chunkX, chunkZ) != null;
    }

    /**
     * Trouve tous les PlotGroups appartenant à une entreprise (par SIRET)
     * @param companySiret Le SIRET de l'entreprise
     * @return Liste des groupes appartenant à cette entreprise
     */
    public List<PlotGroup> getCompanyOwnedGroups(String companySiret) {
        List<PlotGroup> companyGroups = new ArrayList<>();

        for (PlotGroup group : plotGroups.values()) {
            if (companySiret != null && companySiret.equals(group.getCompanySiret())) {
                companyGroups.add(group);
            }
        }

        return companyGroups;
    }

    // ========== FIN NOUVEAU SYSTÈME ==========

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
            if (group.getChunkKeys().size() < 2) {
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
        private final PlotGroup group; // null si dette individuelle
        private final double amount;
        private final LocalDateTime warningDate;
        private final boolean isGroup;

        public PlayerDebt(Plot plot, PlotGroup group, double amount, LocalDateTime warningDate, boolean isGroup) {
            this.plot = plot;
            this.group = group;
            this.amount = amount;
            this.warningDate = warningDate;
            this.isGroup = isGroup;
        }

        public Plot getPlot() { return plot; }
        public PlotGroup getGroup() { return group; }
        public double getAmount() { return amount; }
        public LocalDateTime getWarningDate() { return warningDate; }
        public boolean isGroup() { return isGroup; }
    }

    /**
     * Récupère toutes les dettes d'un joueur (particulier + entreprise)
     */
    public List<PlayerDebt> getPlayerDebts(UUID playerUuid) {
        List<PlayerDebt> debts = new ArrayList<>();

        // ⚠️ NOUVEAU SYSTÈME : Parcourir tous les groupes autonomes pour leurs dettes
        for (PlotGroup group : plotGroups.values()) {
            UUID ownerUuid = group.getOwnerUuid();
            if (ownerUuid != null && ownerUuid.equals(playerUuid)) {
                // Les dettes sont maintenant stockées directement dans le PlotGroup
                // Prioriser la dette entreprise si elle existe
                if (group.getCompanyDebtAmount() > 0) {
                    debts.add(new PlayerDebt(
                        null,  // Pas de plot individuel pour un groupe
                        group,
                        group.getCompanyDebtAmount(),
                        group.getLastDebtWarningDate(),
                        true
                    ));
                } else if (group.getParticularDebtAmount() > 0) {
                    debts.add(new PlayerDebt(
                        null,  // Pas de plot individuel pour un groupe
                        group,
                        group.getParticularDebtAmount(),
                        group.getParticularLastDebtWarningDate(),
                        true
                    ));
                }
            }
        }

        // ⚠️ NOUVEAU SYSTÈME : Parcourir toutes les parcelles individuelles (non groupées)
        Set<String> plotsInGroups = new HashSet<>();
        for (PlotGroup group : plotGroups.values()) {
            plotsInGroups.addAll(group.getChunkKeys());  // ✅ Utiliser getChunkKeys()
        }

        for (Plot plot : plots.values()) {
            String plotKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();

            // Ignorer les parcelles dans des groupes (déjà traitées)
            if (plotsInGroups.contains(plotKey)) {
                continue;
            }

            // Vérifier si le joueur est propriétaire ou locataire
            boolean isOwner = plot.getOwnerUuid() != null && plot.getOwnerUuid().equals(playerUuid);
            boolean isRenter = plot.getRenterUuid() != null && plot.getRenterUuid().equals(playerUuid);

            if (isOwner || isRenter) {
                // FIX: Une parcelle ne doit avoir qu'UNE seule dette (entreprise OU particulier, pas les deux)
                // Prioriser la dette entreprise si elle existe
                if (plot.getCompanyDebtAmount() > 0) {
                    debts.add(new PlayerDebt(
                        plot,
                        null,
                        plot.getCompanyDebtAmount(),
                        plot.getLastDebtWarningDate(),
                        false
                    ));
                } else if (plot.getParticularDebtAmount() > 0) {
                    debts.add(new PlayerDebt(
                        plot,
                        null,
                        plot.getParticularDebtAmount(),
                        plot.getParticularLastDebtWarningDate(),
                        false
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
