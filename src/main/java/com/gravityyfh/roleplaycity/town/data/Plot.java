package com.gravityyfh.roleplaycity.town.data;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;

import java.time.LocalDateTime;
import java.util.*;

public class Plot {
    private final String townName;
    private final int chunkX;
    private final int chunkZ;
    private final String worldName;

    private PlotType type;
    private MunicipalSubType municipalSubType;

    private UUID ownerUuid; // Pour PARTICULIER : UUID joueur, Pour PROFESSIONNEL : UUID gérant
    private String ownerName;
    private String companyName; // Pour PROFESSIONNEL uniquement
    private String companySiret; // SIRET de l'entreprise propriétaire (PROFESSIONNEL uniquement)

    // Système de dette pour terrains PROFESSIONNEL
    private double companyDebtAmount; // Montant de la dette accumulée
    private LocalDateTime lastDebtWarningDate; // Date du dernier avertissement
    private int debtWarningCount; // Nombre d'avertissements envoyés

    private double salePrice;
    private boolean forSale;

    private double rentPricePerDay; // Prix par jour
    private boolean forRent;
    private UUID renterUuid;
    private String renterCompanySiret; // SIRET de l'entreprise du locataire (PROFESSIONNEL uniquement)
    private LocalDateTime rentStartDate;
    private LocalDateTime lastRentUpdate; // Dernière mise à jour du solde
    private int rentDaysRemaining; // Solde de jours restants (max 30)

    // Blocs existants lors de la mise en location (protégés contre le locataire)
    private Set<String> protectedBlocks; // Format: "x:y:z"

    // NOUVEAU : Tracker des blocs placés par le locataire
    private RenterBlockTracker renterBlockTracker;

    private final LocalDateTime claimDate;

    // Système de permissions par joueur
    private final Map<UUID, Set<PlotPermission>> playerPermissions;

    // Liste d'amis avec toutes les permissions
    private final Set<UUID> trustedPlayers;

    // Flags de protection
    private final Map<PlotFlag, Boolean> flags;

    public Plot(String townName, Chunk chunk) {
        this.townName = townName;
        this.chunkX = chunk.getX();
        this.chunkZ = chunk.getZ();
        this.worldName = chunk.getWorld().getName();
        this.type = PlotType.MUNICIPAL; // Type par défaut
        this.municipalSubType = MunicipalSubType.NONE;
        this.claimDate = LocalDateTime.now();
        this.forSale = false;
        this.forRent = false;
        this.rentDaysRemaining = 0;
        this.companyDebtAmount = 0.0;
        this.debtWarningCount = 0;
        this.protectedBlocks = new HashSet<>();
        this.renterBlockTracker = new RenterBlockTracker();
        this.playerPermissions = new HashMap<>();
        this.trustedPlayers = new HashSet<>();
        this.flags = new EnumMap<>(PlotFlag.class);

        // Initialiser les flags avec leurs valeurs par défaut
        for (PlotFlag flag : PlotFlag.values()) {
            flags.put(flag, flag.getDefaultValue());
        }
    }

    // Getters
    public String getTownName() { return townName; }
    public int getChunkX() { return chunkX; }
    public int getChunkZ() { return chunkZ; }
    public String getWorldName() { return worldName; }
    public PlotType getType() { return type; }
    public MunicipalSubType getMunicipalSubType() { return municipalSubType; }
    public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }
    public String getCompanyName() { return companyName; }
    public String getCompanySiret() { return companySiret; }
    public double getCompanyDebtAmount() { return companyDebtAmount; }
    public LocalDateTime getLastDebtWarningDate() { return lastDebtWarningDate; }
    public int getDebtWarningCount() { return debtWarningCount; }
    public double getSalePrice() { return salePrice; }
    public boolean isForSale() { return forSale; }
    public double getRentPricePerDay() { return rentPricePerDay; }

    /**
     * @deprecated Utilisez getRentPricePerDay() * getRentDaysRemaining() pour le prix total
     */
    @Deprecated
    public double getRentPrice() { return rentPricePerDay * Math.max(1, rentDaysRemaining); }

    /**
     * @deprecated Utilisez getRentDaysRemaining()
     */
    @Deprecated
    public int getRentDurationDays() { return rentDaysRemaining; }

    public boolean isForRent() { return forRent; }
    public UUID getRenterUuid() { return renterUuid; }
    public String getRenterCompanySiret() { return renterCompanySiret; }
    public LocalDateTime getRentStartDate() { return rentStartDate; }
    public LocalDateTime getLastRentUpdate() { return lastRentUpdate; }
    public int getRentDaysRemaining() { return rentDaysRemaining; }
    public LocalDateTime getClaimDate() { return claimDate; }
    public Set<String> getProtectedBlocks() { return new HashSet<>(protectedBlocks); }
    public RenterBlockTracker getRenterBlockTracker() { return renterBlockTracker; }

    // Setters
    public void setType(PlotType type) { this.type = type; }
    public void setMunicipalSubType(MunicipalSubType subType) { this.municipalSubType = subType; }

    public void setOwner(UUID ownerUuid, String ownerName) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
    }

    public void setCompany(String companyName) {
        this.companyName = companyName;
    }

    public void setCompanySiret(String companySiret) {
        this.companySiret = companySiret;
    }

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

    public void setSalePrice(double price) {
        this.salePrice = price;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
    }

    /**
     * @deprecated Utilisez setRentPricePerDay()
     */
    @Deprecated
    public void setRent(double totalPrice, int durationDays) {
        this.rentPricePerDay = totalPrice / Math.max(1, durationDays);
        this.rentDaysRemaining = durationDays;
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
        String oldRenterSiret = this.renterCompanySiret; // SAUVEGARDER avant de clear

        this.renterUuid = null;
        this.renterCompanySiret = null;
        this.rentStartDate = null;
        this.lastRentUpdate = null;
        this.rentDaysRemaining = 0;
        this.protectedBlocks.clear();

        // NOUVEAU : Nettoyer le tracker des blocs du locataire
        if (oldRenter != null && renterBlockTracker != null) {
            renterBlockTracker.clearRenter(oldRenter);
        }

        // NOUVEAU : Supprimer les shops du locataire sur ce terrain
        if (oldRenterSiret != null) {
            com.gravityyfh.roleplaycity.Shop.ShopManager shopManager =
                ((com.gravityyfh.roleplaycity.RoleplayCity) org.bukkit.Bukkit.getPluginManager().getPlugin("RoleplayCity")).getShopManager();
            if (shopManager != null) {
                shopManager.deleteShopsByCompanyOnPlot(
                    oldRenterSiret,
                    this,
                    true, // Notifier
                    "Fin de la location du terrain"
                );
            }
        }
    }

    /**
     * Recharger le solde de location (max 30 jours total)
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
     * Mettre à jour le solde (appelé chaque jour)
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
                // Location expirée
                clearRenter();
            }
        }
    }

    /**
     * Enregistrer tous les blocs existants dans le chunk (appelé lors de la mise en location)
     */
    public void scanAndProtectExistingBlocks(Chunk chunk) {
        protectedBlocks.clear();
        int minY = chunk.getWorld().getMinHeight();
        int maxY = chunk.getWorld().getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    org.bukkit.block.Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != org.bukkit.Material.AIR) {
                        int worldX = chunk.getX() * 16 + x;
                        int worldZ = chunk.getZ() * 16 + z;
                        protectedBlocks.add(worldX + ":" + y + ":" + worldZ);
                    }
                }
            }
        }
    }

    /**
     * Vérifier si un bloc est protégé
     */
    public boolean isBlockProtected(Location location) {
        String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        return protectedBlocks.contains(key);
    }

    /**
     * Ajouter un bloc comme protégé (quand le propriétaire place un bloc)
     */
    public void addProtectedBlock(Location location) {
        String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        protectedBlocks.add(key);
    }

    /**
     * Retirer un bloc de la protection (quand le propriétaire casse un bloc)
     */
    public void removeProtectedBlock(Location location) {
        String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        protectedBlocks.remove(key);
    }

    public void clearOwner() {
        this.ownerUuid = null;
        this.ownerName = null;
        this.companyName = null;
        this.forSale = false;
        this.forRent = false;
        clearRenter();
    }

    // Utility methods
    public boolean isOwnedBy(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    public boolean isRentedBy(UUID playerUuid) {
        return renterUuid != null && renterUuid.equals(playerUuid);
    }

    /**
     * @deprecated Le système utilise maintenant updateRentDays() et rentDaysRemaining
     */
    @Deprecated
    public boolean isRentExpired() {
        return rentDaysRemaining <= 0;
    }

    public boolean isMunicipal() {
        return type == PlotType.MUNICIPAL;
    }

    public boolean isPublic() {
        return type == PlotType.PUBLIC;
    }

    public boolean requiresCompany() {
        return type.requiresCompany();
    }

    public double getDailyTax() {
        return type.getDailyTax();
    }

    public boolean canPlayerBuild(UUID playerUuid, TownRole role) {
        // Public : tout le monde peut construire
        if (isPublic()) {
            return true;
        }

        // Municipal : architecte, maire ou adjoint
        if (isMunicipal()) {
            return role == TownRole.ARCHITECTE || role == TownRole.MAIRE || role == TownRole.ADJOINT;
        }

        // Particulier/Professionnel : propriétaire ou locataire
        return isOwnedBy(playerUuid) || isRentedBy(playerUuid);
    }

    /**
     * Vérifie si un joueur peut construire sur cette parcelle (avec contexte de ville)
     */
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

        return canPlayerBuild(playerUuid, role);
    }

    /**
     * Vérifie si un joueur peut interagir avec les blocs de cette parcelle
     */
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
        return role == TownRole.MAIRE || role == TownRole.ADJOINT;
    }

    public String getCoordinates() {
        return String.format("(%d, %d)", chunkX, chunkZ);
    }

    public boolean matchesChunk(Chunk chunk) {
        return chunk.getX() == chunkX &&
               chunk.getZ() == chunkZ &&
               chunk.getWorld().getName().equals(worldName);
    }

    // ========== Gestion des permissions ==========

    /**
     * Ajouter une permission à un joueur
     */
    public void addPermission(UUID playerUuid, PlotPermission permission) {
        playerPermissions.computeIfAbsent(playerUuid, k -> EnumSet.noneOf(PlotPermission.class))
            .add(permission);
    }

    /**
     * Retirer une permission à un joueur
     */
    public void removePermission(UUID playerUuid, PlotPermission permission) {
        Set<PlotPermission> perms = playerPermissions.get(playerUuid);
        if (perms != null) {
            perms.remove(permission);
            if (perms.isEmpty()) {
                playerPermissions.remove(playerUuid);
            }
        }
    }

    /**
     * Vérifier si un joueur a une permission spécifique
     */
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

    /**
     * Obtenir toutes les permissions d'un joueur
     */
    public Set<PlotPermission> getPlayerPermissions(UUID playerUuid) {
        if (isOwnedBy(playerUuid) || isRentedBy(playerUuid) || trustedPlayers.contains(playerUuid)) {
            return EnumSet.allOf(PlotPermission.class);
        }
        return new HashSet<>(playerPermissions.getOrDefault(playerUuid, Collections.emptySet()));
    }

    /**
     * Définir toutes les permissions d'un joueur
     */
    public void setPlayerPermissions(UUID playerUuid, Set<PlotPermission> permissions) {
        if (permissions.isEmpty()) {
            playerPermissions.remove(playerUuid);
        } else {
            playerPermissions.put(playerUuid, EnumSet.copyOf(permissions));
        }
    }

    /**
     * Retirer toutes les permissions d'un joueur
     */
    public void clearPlayerPermissions(UUID playerUuid) {
        playerPermissions.remove(playerUuid);
    }

    /**
     * Obtenir tous les joueurs avec des permissions
     */
    public Map<UUID, Set<PlotPermission>> getAllPlayerPermissions() {
        return new HashMap<>(playerPermissions);
    }

    // ========== Gestion des joueurs de confiance ==========

    /**
     * Ajouter un joueur de confiance (toutes permissions)
     */
    public void addTrustedPlayer(UUID playerUuid) {
        trustedPlayers.add(playerUuid);
    }

    /**
     * Retirer un joueur de confiance
     */
    public void removeTrustedPlayer(UUID playerUuid) {
        trustedPlayers.remove(playerUuid);
    }

    /**
     * Vérifier si un joueur est de confiance
     */
    public boolean isTrusted(UUID playerUuid) {
        return trustedPlayers.contains(playerUuid);
    }

    /**
     * Obtenir tous les joueurs de confiance
     */
    public Set<UUID> getTrustedPlayers() {
        return new HashSet<>(trustedPlayers);
    }

    // ========== Gestion des flags ==========

    /**
     * Définir un flag
     */
    public void setFlag(PlotFlag flag, boolean value) {
        flags.put(flag, value);
    }

    /**
     * Obtenir la valeur d'un flag
     */
    public boolean getFlag(PlotFlag flag) {
        return flags.getOrDefault(flag, flag.getDefaultValue());
    }

    /**
     * Obtenir tous les flags
     */
    public Map<PlotFlag, Boolean> getAllFlags() {
        return new EnumMap<>(flags);
    }

    /**
     * Réinitialiser un flag à sa valeur par défaut
     */
    public void resetFlag(PlotFlag flag) {
        flags.put(flag, flag.getDefaultValue());
    }

    /**
     * Réinitialiser tous les flags
     */
    public void resetAllFlags() {
        for (PlotFlag flag : PlotFlag.values()) {
            flags.put(flag, flag.getDefaultValue());
        }
    }

    /**
     * Définit directement le nombre de jours restants (utilisé pour synchronisation avec groupe)
     */
    public void setRentDaysRemaining(int days) {
        this.rentDaysRemaining = days;
    }
}
