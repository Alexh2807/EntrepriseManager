package com.gravityyfh.roleplaycity.town.data;

import org.bukkit.Location;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Représente un groupe de parcelles fusionnées en une entité terrain unique
 *
 * ⚠️ IMPORTANT : Quand des parcelles sont groupées, elles n'existent plus individuellement.
 * Le PlotGroup devient l'unique représentant du terrain avec toutes ses propriétés.
 *
 * Ce système remplace complètement les plots individuels pour simplifier la gestion
 * et éviter les incohérences entre plots et groupes.
 */
public class PlotGroup implements TerritoryEntity {

    // ========== IDENTIFICATION ==========
    private final String groupId; // UUID unique du groupe
    private final String townName;
    private final Set<String> chunkKeys; // Format: "world:chunkX:chunkZ" - Chunks occupés par ce groupe
    private final LocalDateTime creationDate;
    private String groupName; // Nom personnalisable du groupe

    // ========== PROPRIÉTÉS TERRAIN (Autonome comme un Plot) ==========
    private PlotType type; // Type de terrain (MUNICIPAL, PUBLIC, PARTICULIER, PROFESSIONNEL)
    private MunicipalSubType municipalSubType; // Sous-type si municipal

    // Propriétaire
    private UUID ownerUuid; // UUID du propriétaire (gérant pour PROFESSIONNEL)
    private String ownerName; // Nom du propriétaire
    private String companyName; // Nom de l'entreprise (PROFESSIONNEL uniquement)
    private String companySiret; // SIRET de l'entreprise propriétaire (PROFESSIONNEL uniquement)
    private LocalDateTime claimDate; // Date de création/claim du groupe

    // ========== SYSTÈME DE DETTES ==========

    // Dettes pour terrains PROFESSIONNEL (entreprises)
    private double companyDebtAmount; // Montant de la dette accumulée
    private LocalDateTime lastDebtWarningDate; // Date du dernier avertissement
    private int debtWarningCount; // Nombre d'avertissements envoyés

    // Dettes pour terrains PARTICULIER (citoyens)
    private double particularDebtAmount; // Montant de la dette accumulée pour particuliers
    private LocalDateTime particularLastDebtWarningDate; // Date du dernier avertissement
    private int particularDebtWarningCount; // Nombre d'avertissements envoyés

    // ========== VENTE ET LOCATION ==========

    // Vente
    private double salePrice; // Prix de vente du groupe entier
    private boolean forSale; // Le groupe est-il en vente ?

    // Location
    private double rentPricePerDay; // Prix de location par jour
    private boolean forRent; // Le groupe est-il en location ?
    private UUID renterUuid; // UUID du locataire actuel
    private String renterCompanySiret; // SIRET de l'entreprise locataire (PROFESSIONNEL uniquement)
    private LocalDateTime rentStartDate; // Date de début de location
    private LocalDateTime lastRentUpdate; // Dernière mise à jour du solde
    private int rentDaysRemaining; // Solde de jours restants (max 30)

    // ========== PROTECTION BLOCS ==========

    // Blocs existants lors de la mise en location (protégés contre le locataire)
    private Set<String> protectedBlocks; // Format: "x:y:z"

    // Tracker des blocs placés par le locataire
    private RenterBlockTracker renterBlockTracker;

    // ========== SYSTÈME DE PERMISSIONS ==========

    // Permissions par joueur
    private final Map<UUID, Set<PlotPermission>> playerPermissions;

    // Liste d'amis avec toutes les permissions
    private final Set<UUID> trustedPlayers;

    // ========== FLAGS DE PROTECTION ==========

    private final Map<PlotFlag, Boolean> flags;

    // ========== CONSTRUCTEUR ==========

    public PlotGroup(String groupId, String townName) {
        this.groupId = groupId;
        this.townName = townName;
        this.chunkKeys = new HashSet<>();
        this.creationDate = LocalDateTime.now();
        this.claimDate = LocalDateTime.now();

        // Valeurs par défaut
        this.groupName = "Groupe-" + groupId.substring(0, 8);
        this.type = PlotType.MUNICIPAL; // Type par défaut
        this.municipalSubType = MunicipalSubType.NONE;
        this.forSale = false;
        this.forRent = false;
        this.rentDaysRemaining = 0;

        // Dettes
        this.companyDebtAmount = 0.0;
        this.debtWarningCount = 0;
        this.particularDebtAmount = 0.0;
        this.particularDebtWarningCount = 0;

        // Protection
        this.protectedBlocks = new HashSet<>();
        this.renterBlockTracker = new RenterBlockTracker();

        // Permissions
        this.playerPermissions = new HashMap<>();
        this.trustedPlayers = new HashSet<>();

        // Flags
        this.flags = new EnumMap<>(PlotFlag.class);
        for (PlotFlag flag : PlotFlag.values()) {
            flags.put(flag, flag.getDefaultValue());
        }
    }

    // ========== GESTION DES CHUNKS ==========

    /**
     * Ajoute un chunk au groupe
     * @param chunkKey Format: "world:chunkX:chunkZ"
     * @return true si ajouté avec succès
     */
    public boolean addChunk(String chunkKey) {
        return chunkKeys.add(chunkKey);
    }

    /**
     * Retire un chunk du groupe
     * @param chunkKey Format: "world:chunkX:chunkZ"
     * @return true si retiré avec succès
     */
    public boolean removeChunk(String chunkKey) {
        return chunkKeys.remove(chunkKey);
    }

    /**
     * Vérifie si un chunk fait partie du groupe
     * @param chunkKey Format: "world:chunkX:chunkZ"
     */
    public boolean containsChunk(String chunkKey) {
        return chunkKeys.contains(chunkKey);
    }

    /**
     * Ajoute une parcelle au groupe (pour compatibilité avec ancien code)
     * @deprecated Utilisez addChunk() directement
     */
    @Deprecated
    public boolean addPlot(Plot plot) {
        String chunkKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();
        return addChunk(chunkKey);
    }

    /**
     * Retire une parcelle du groupe (pour compatibilité avec ancien code)
     * @deprecated Utilisez removeChunk() directement
     */
    @Deprecated
    public boolean removePlot(Plot plot) {
        String chunkKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();
        return removeChunk(chunkKey);
    }

    /**
     * Vérifie si une parcelle fait partie du groupe (pour compatibilité avec ancien code)
     * @deprecated Utilisez containsChunk() directement
     */
    @Deprecated
    public boolean containsPlot(Plot plot) {
        String chunkKey = plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ();
        return containsChunk(chunkKey);
    }

    /**
     * @return Le nombre de chunks dans ce groupe
     */
    public int getChunkCount() {
        return chunkKeys.size();
    }

    /**
     * @deprecated Utilisez getChunkCount()
     */
    @Deprecated
    public int getPlotCount() {
        return getChunkCount();
    }

    /**
     * Vérifie si le groupe a au moins 2 chunks (minimum pour un groupe valide)
     */
    public boolean isValid() {
        return chunkKeys.size() >= 2;
    }

    // ========== IMPLÉMENTATION TerritoryEntity ==========

    @Override
    public String getIdentifier() {
        return groupId;
    }

    @Override
    public String getTownName() {
        return townName;
    }

    @Override
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    @Override
    public String getOwnerName() {
        return ownerName;
    }

    @Override
    public PlotType getType() {
        return type;
    }

    @Override
    public MunicipalSubType getMunicipalSubType() {
        return municipalSubType;
    }

    @Override
    public String getCompanyName() {
        return companyName;
    }

    @Override
    public String getCompanySiret() {
        return companySiret;
    }

    @Override
    public LocalDateTime getClaimDate() {
        return claimDate;
    }

    @Override
    public boolean isOwnedBy(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    @Override
    public double getCompanyDebtAmount() {
        return companyDebtAmount;
    }

    @Override
    public double getParticularDebtAmount() {
        return particularDebtAmount;
    }

    @Override
    public int getDebtWarningCount() {
        return debtWarningCount;
    }

    @Override
    public int getParticularDebtWarningCount() {
        return particularDebtWarningCount;
    }

    @Override
    public LocalDateTime getLastDebtWarningDate() {
        return lastDebtWarningDate;
    }

    @Override
    public LocalDateTime getParticularLastDebtWarningDate() {
        return particularLastDebtWarningDate;
    }

    @Override
    public boolean isForSale() {
        return forSale;
    }

    @Override
    public boolean isForRent() {
        return forRent;
    }

    @Override
    public double getSalePrice() {
        return salePrice;
    }

    @Override
    public double getRentPricePerDay() {
        return rentPricePerDay;
    }

    @Override
    public UUID getRenterUuid() {
        return renterUuid;
    }

    @Override
    public String getRenterCompanySiret() {
        return renterCompanySiret;
    }

    @Override
    public int getRentDaysRemaining() {
        return rentDaysRemaining;
    }

    @Override
    public LocalDateTime getRentStartDate() {
        return rentStartDate;
    }

    @Override
    public boolean isRentedBy(UUID playerUuid) {
        return renterUuid != null && renterUuid.equals(playerUuid);
    }

    @Override
    public double getDailyTax() {
        return type != null ? type.getDailyTax() * chunkKeys.size() : 0.0;
    }

    @Override
    public boolean canBuild(UUID playerUuid, Town town) {
        // Public : tout le monde peut construire
        if (isPublic()) {
            return true;
        }

        // Récupérer le rôle du joueur
        TownRole role = town.getMemberRole(playerUuid);
        if (role == null) {
            return false; // Pas membre de la ville
        }

        // Municipal : architecte, maire ou adjoint
        if (isMunicipal()) {
            return role == TownRole.ARCHITECTE || role == TownRole.MAIRE || role == TownRole.ADJOINT;
        }

        // Particulier/Professionnel : propriétaire, locataire, ou permissions
        if (isOwnedBy(playerUuid) || isRentedBy(playerUuid)) {
            return true;
        }

        // Vérifier les permissions individuelles
        return hasPermission(playerUuid, PlotPermission.BUILD);
    }

    @Override
    public boolean canInteract(UUID playerUuid, Town town) {
        // Public : tout le monde peut interagir
        if (isPublic()) {
            return true;
        }

        // Municipal : tous les membres de la ville peuvent interagir
        if (isMunicipal()) {
            return town.isMember(playerUuid);
        }

        // Particulier/Professionnel : propriétaire, locataire, ou membres avec permission
        if (isOwnedBy(playerUuid) || isRentedBy(playerUuid)) {
            return true;
        }

        // Les adjoints et le maire peuvent toujours interagir
        TownRole role = town.getMemberRole(playerUuid);
        if (role == TownRole.MAIRE || role == TownRole.ADJOINT) {
            return true;
        }

        // Vérifier les permissions individuelles
        return hasPermission(playerUuid, PlotPermission.INTERACT);
    }

    @Override
    public boolean hasPermission(UUID playerUuid, PlotPermission permission) {
        // Propriétaire et locataire ont toutes les permissions
        if (isOwnedBy(playerUuid) || isRentedBy(playerUuid)) {
            return true;
        }

        // Joueurs de confiance ont toutes les permissions
        if (trustedPlayers.contains(playerUuid)) {
            return true;
        }

        // Vérifier les permissions individuelles
        Set<PlotPermission> perms = playerPermissions.get(playerUuid);
        return perms != null && perms.contains(permission);
    }

    @Override
    public Set<PlotPermission> getPlayerPermissions(UUID playerUuid) {
        if (isOwnedBy(playerUuid) || isRentedBy(playerUuid) || trustedPlayers.contains(playerUuid)) {
            return EnumSet.allOf(PlotPermission.class);
        }
        return new HashSet<>(playerPermissions.getOrDefault(playerUuid, Collections.emptySet()));
    }

    @Override
    public Map<UUID, Set<PlotPermission>> getAllPlayerPermissions() {
        return new HashMap<>(playerPermissions);
    }

    @Override
    public Set<UUID> getTrustedPlayers() {
        return new HashSet<>(trustedPlayers);
    }

    @Override
    public boolean isTrusted(UUID playerUuid) {
        return trustedPlayers.contains(playerUuid);
    }

    @Override
    public boolean getFlag(PlotFlag flag) {
        return flags.getOrDefault(flag, flag.getDefaultValue());
    }

    @Override
    public Map<PlotFlag, Boolean> getAllFlags() {
        return new EnumMap<>(flags);
    }

    @Override
    public RenterBlockTracker getRenterBlockTracker() {
        return renterBlockTracker;
    }

    // ========== SETTERS PROPRIÉTÉ ==========

    public void setType(PlotType type) {
        this.type = type;
    }

    public void setMunicipalSubType(MunicipalSubType subType) {
        this.municipalSubType = subType;
    }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public void setCompanySiret(String companySiret) {
        this.companySiret = companySiret;
    }

    public void setClaimDate(LocalDateTime date) {
        this.claimDate = date;
    }

    public void clearOwner() {
        this.ownerUuid = null;
        this.ownerName = null;
        this.companyName = null;
        this.companySiret = null;
        this.forSale = false;
        this.forRent = false;
        clearRenter();
    }

    // ========== SETTERS DETTES ==========

    public void setCompanyDebtAmount(double amount) {
        this.companyDebtAmount = amount;
    }

    public void setLastDebtWarningDate(LocalDateTime date) {
        this.lastDebtWarningDate = date;
    }

    public void setDebtWarningCount(int count) {
        this.debtWarningCount = count;
    }

    public void resetDebt() {
        this.companyDebtAmount = 0.0;
        this.debtWarningCount = 0;
        this.lastDebtWarningDate = null;
    }

    public void setParticularDebtAmount(double amount) {
        this.particularDebtAmount = amount;
    }

    public void setParticularLastDebtWarningDate(LocalDateTime date) {
        this.particularLastDebtWarningDate = date;
    }

    public void setParticularDebtWarningCount(int count) {
        this.particularDebtWarningCount = count;
    }

    public void resetParticularDebt() {
        this.particularDebtAmount = 0.0;
        this.particularDebtWarningCount = 0;
        this.particularLastDebtWarningDate = null;
    }

    // ========== SETTERS VENTE/LOCATION ==========

    public void setSalePrice(double price) {
        this.salePrice = price;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
    }

    public void setRentPricePerDay(double pricePerDay) {
        this.rentPricePerDay = pricePerDay;
    }

    public void setForRent(boolean forRent) {
        this.forRent = forRent;
    }

    public void setRenter(UUID renterUuid, int initialDays) {
        this.renterUuid = renterUuid;
        this.rentStartDate = LocalDateTime.now();
        this.lastRentUpdate = LocalDateTime.now();
        this.rentDaysRemaining = Math.min(initialDays, 30); // Max 30 jours
    }

    public void setRenterCompanySiret(String renterCompanySiret) {
        this.renterCompanySiret = renterCompanySiret;
    }

    public void clearRenter() {
        UUID oldRenter = this.renterUuid;
        String oldRenterSiret = this.renterCompanySiret;

        this.renterUuid = null;
        this.renterCompanySiret = null;
        this.rentStartDate = null;
        this.lastRentUpdate = null;
        this.rentDaysRemaining = 0;
        this.protectedBlocks.clear();

        // Nettoyer le tracker des blocs du locataire
        if (oldRenter != null && renterBlockTracker != null) {
            renterBlockTracker.clearRenter(oldRenter);
        }

        // Supprimer les shops du locataire sur ce groupe
        if (oldRenterSiret != null) {
            com.gravityyfh.roleplaycity.Shop.ShopManager shopManager =
                ((com.gravityyfh.roleplaycity.RoleplayCity) org.bukkit.Bukkit.getPluginManager().getPlugin("RoleplayCity")).getShopManager();
            if (shopManager != null) {
                // Pour chaque chunk du groupe, supprimer les shops
                for (String chunkKey : chunkKeys) {
                    String[] parts = chunkKey.split(":");
                    if (parts.length == 3) {
                        String worldName = parts[0];
                        int chunkX = Integer.parseInt(parts[1]);
                        int chunkZ = Integer.parseInt(parts[2]);

                        // Obtenir le chunk Bukkit pour créer un Plot temporaire
                        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                        if (world != null) {
                            org.bukkit.Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                            // Créer un Plot temporaire pour passer au ShopManager
                            // (le ShopManager a besoin d'un Plot uniquement pour les coordonnées)
                            Plot tempPlot = new Plot(townName, chunk);
                            shopManager.deleteShopsByCompanyOnPlot(
                                oldRenterSiret,
                                tempPlot,
                                true,
                                "Fin de la location du groupe"
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * Recharge le solde de location (max 30 jours)
     */
    public int rechargeDays(int daysToAdd) {
        int maxDays = 30;
        int newTotal = this.rentDaysRemaining + daysToAdd;
        if (newTotal > maxDays) {
            int actualAdded = maxDays - this.rentDaysRemaining;
            this.rentDaysRemaining = maxDays;
            return actualAdded;
        }
        this.rentDaysRemaining = newTotal;
        return daysToAdd;
    }

    /**
     * Met à jour le solde de jours (appelé automatiquement chaque jour)
     */
    public void updateRentDays() {
        if (lastRentUpdate == null || renterUuid == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        long daysPassed = java.time.Duration.between(lastRentUpdate, now).toDays();

        if (daysPassed > 0) {
            rentDaysRemaining -= (int) daysPassed;
            lastRentUpdate = now;

            if (rentDaysRemaining <= 0) {
                clearRenter();
            }
        }
    }

    /**
     * Définit directement le nombre de jours restants (pour synchronisation)
     */
    public void setRentDaysRemaining(int days) {
        this.rentDaysRemaining = days;
    }

    public void setLastRentUpdate(LocalDateTime lastRentUpdate) {
        this.lastRentUpdate = lastRentUpdate;
    }

    // ========== GESTION PERMISSIONS ==========

    public void addPermission(UUID playerUuid, PlotPermission permission) {
        playerPermissions.computeIfAbsent(playerUuid, k -> EnumSet.noneOf(PlotPermission.class))
            .add(permission);
    }

    public void removePermission(UUID playerUuid, PlotPermission permission) {
        Set<PlotPermission> perms = playerPermissions.get(playerUuid);
        if (perms != null) {
            perms.remove(permission);
            if (perms.isEmpty()) {
                playerPermissions.remove(playerUuid);
            }
        }
    }

    public void setPlayerPermissions(UUID playerUuid, Set<PlotPermission> permissions) {
        if (permissions.isEmpty()) {
            playerPermissions.remove(playerUuid);
        } else {
            playerPermissions.put(playerUuid, EnumSet.copyOf(permissions));
        }
    }

    public void clearPlayerPermissions(UUID playerUuid) {
        playerPermissions.remove(playerUuid);
    }

    public void addTrustedPlayer(UUID playerUuid) {
        trustedPlayers.add(playerUuid);
    }

    public void removeTrustedPlayer(UUID playerUuid) {
        trustedPlayers.remove(playerUuid);
    }

    // ========== GESTION FLAGS ==========

    public void setFlag(PlotFlag flag, boolean value) {
        flags.put(flag, value);
    }

    public void resetFlag(PlotFlag flag) {
        flags.put(flag, flag.getDefaultValue());
    }

    public void resetAllFlags() {
        for (PlotFlag flag : PlotFlag.values()) {
            flags.put(flag, flag.getDefaultValue());
        }
    }

    // ========== PROTECTION BLOCS ==========

    public Set<String> getProtectedBlocks() {
        return new HashSet<>(protectedBlocks);
    }

    public boolean isBlockProtected(Location location) {
        String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        return protectedBlocks.contains(key);
    }

    public void addProtectedBlock(Location location) {
        String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        protectedBlocks.add(key);
    }

    public void removeProtectedBlock(Location location) {
        String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        protectedBlocks.remove(key);
    }

    /**
     * Scanne et protège tous les blocs existants dans un chunk du groupe
     * Utilisé lors de la mise en location pour empêcher le locataire de détruire les blocs préexistants
     */
    public void scanAndProtectExistingBlocks(org.bukkit.Chunk chunk) {
        org.bukkit.World world = chunk.getWorld();
        int chunkX = chunk.getX() * 16;
        int chunkZ = chunk.getZ() * 16;

        // Scanner tous les blocs du chunk
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    org.bukkit.block.Block block = world.getBlockAt(chunkX + x, y, chunkZ + z);

                    // Protéger seulement les blocs non-air
                    if (block.getType() != org.bukkit.Material.AIR &&
                        block.getType() != org.bukkit.Material.CAVE_AIR &&
                        block.getType() != org.bukkit.Material.VOID_AIR) {
                        addProtectedBlock(block.getLocation());
                    }
                }
            }
        }
    }

    // ========== GETTERS IDENTIFICATION ==========

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Set<String> getChunkKeys() {
        return new HashSet<>(chunkKeys);
    }

    /**
     * @deprecated Utilisez getChunkKeys()
     */
    @Deprecated
    public Set<String> getPlotKeys() {
        return getChunkKeys();
    }

    public LocalDateTime getCreationDate() {
        return creationDate;
    }

    public LocalDateTime getLastRentUpdate() {
        return lastRentUpdate;
    }

    // ========== MÉTHODES UTILITAIRES ==========

    /**
     * @deprecated Méthode obsolète - Le PlotGroup a maintenant ses propres prix
     */
    @Deprecated
    public double calculateTotalRentPrice(List<Plot> plots) {
        return rentPricePerDay;
    }

    /**
     * @deprecated Méthode obsolète - Le PlotGroup a maintenant ses propres prix
     */
    @Deprecated
    public double calculateTotalSalePrice(List<Plot> plots) {
        return salePrice;
    }

    @Override
    public String toString() {
        return String.format("PlotGroup{id=%s, name=%s, town=%s, chunks=%d, type=%s, owner=%s}",
            groupId.substring(0, 8), groupName, townName, chunkKeys.size(), type, ownerName);
    }
}
