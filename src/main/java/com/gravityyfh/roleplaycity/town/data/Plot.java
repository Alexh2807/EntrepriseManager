package com.gravityyfh.roleplaycity.town.data;

import com.gravityyfh.roleplaycity.util.PlayerNameResolver;
import org.bukkit.Chunk;
import org.bukkit.Location;

import java.time.LocalDateTime;
import java.util.*;

public class Plot {
    private final String townName;

    // ⚠️ NOUVEAU SYSTÈME UNIFIÉ : Support multi-chunks
    private final String worldName;
    private final List<String> chunks; // Format: "world:chunkX:chunkZ"
    private boolean grouped; // true si 2+ chunks
    private String groupName; // Nom du groupe si grouped = true

    // Numéro unique de terrain (ex: V-001 pour Veloria)
    // Seulement pour PARTICULIER, PROFESSIONNEL et MUNICIPAL (pas PUBLIC)
    private String plotNumber;

    private PlotType type;
    private MunicipalSubType municipalSubType;

    private UUID ownerUuid; // Pour PARTICULIER : UUID joueur, Pour PROFESSIONNEL : UUID gérant
    private String storedOwnerName; // Nom stocké pour fallback (peut être obsolète)
    private String companyName; // Pour PROFESSIONNEL uniquement
    private String companySiret; // SIRET de l'entreprise propriétaire (PROFESSIONNEL uniquement)

    // Système de dette pour terrains PROFESSIONNEL
    private double companyDebtAmount; // Montant de la dette accumulée
    private LocalDateTime lastDebtWarningDate; // Date du dernier avertissement
    private int debtWarningCount; // Nombre d'avertissements envoyés

    // NOUVEAU : Système de dette pour terrains PARTICULIER
    private double particularDebtAmount; // Montant de la dette accumulée pour particuliers
    private LocalDateTime particularLastDebtWarningDate; // Date du dernier avertissement
    private int particularDebtWarningCount; // Nombre d'avertissements envoyés

    private double salePrice;
    private boolean forSale;

    private double rentPricePerDay; // Prix par jour
    private boolean forRent;
    private UUID renterUuid;
    private String renterCompanySiret; // SIRET de l'entreprise du locataire (PROFESSIONNEL uniquement)
    private LocalDateTime rentStartDate; // Date de début de location
    private LocalDateTime rentEndDate; // Date de fin de location (expiration)

    // Blocs existants lors de la mise en location (protégés contre le locataire)
    private final Set<String> protectedBlocks; // Format: "x:y:z"

    // NOUVEAU : Tracker des blocs placés par le locataire
    private final RenterBlockTracker renterBlockTracker;

    private LocalDateTime claimDate;

    // Système de permissions par joueur
    private final Map<UUID, Set<PlotPermission>> playerPermissions;

    // Liste d'amis avec toutes les permissions
    private final Set<UUID> trustedPlayers;

    // Flags de protection
    private final Map<PlotFlag, Boolean> flags;

    // ⛓️ Système de prison (pour COMMISSARIAT uniquement)
    private Location prisonSpawnLocation;

    // 📬 Système de boîte aux lettres (intégré dans le plot)
    private com.gravityyfh.roleplaycity.postal.data.Mailbox mailbox;

    // 🔑 Système d'autorisations parentales (propriétaire/locataire -> enfants)
    // Les autorisations sont liées au parent : si le parent perd le terrain, les enfants perdent leurs autorisations
    private final Set<UUID> ownerAuthorizedPlayers; // Joueurs autorisés par le propriétaire
    private final Set<UUID> renterAuthorizedPlayers; // Joueurs autorisés par le locataire
    private static final int MAX_AUTHORIZED_PLAYERS = 5; // Limite par propriétaire/locataire

    /**
     * Constructeur pour un nouveau terrain (1 chunk initial)
     */
    public Plot(String townName, Chunk chunk) {
        this.townName = townName;
        this.worldName = chunk.getWorld().getName();
        this.chunks = new ArrayList<>();
        this.chunks.add(worldName + ":" + chunk.getX() + ":" + chunk.getZ());
        this.grouped = false;
        this.groupName = null;

        this.type = PlotType.PUBLIC; // Type par défaut (terrain public accessible à tous)
        this.municipalSubType = MunicipalSubType.NONE;
        this.claimDate = LocalDateTime.now();
        this.forSale = false;
        this.forRent = false;
        this.rentEndDate = null; // Pas de location active
        this.companyDebtAmount = 0.0;
        this.debtWarningCount = 0;
        this.particularDebtAmount = 0.0;
        this.particularDebtWarningCount = 0;
        this.protectedBlocks = new HashSet<>();
        this.renterBlockTracker = new RenterBlockTracker();
        this.playerPermissions = new HashMap<>();
        this.trustedPlayers = new HashSet<>();
        this.flags = new EnumMap<>(PlotFlag.class);
        this.ownerAuthorizedPlayers = new HashSet<>();
        this.renterAuthorizedPlayers = new HashSet<>();

        // Initialiser les flags avec leurs valeurs par défaut
        for (PlotFlag flag : PlotFlag.values()) {
            flags.put(flag, flag.getDefaultValue());
        }
    }

    /**
     * ⚠️ NOUVEAU : Constructeur pour un terrain multi-chunks (groupe)
     */
    public Plot(String townName, String worldName, List<String> chunkKeys, String groupName) {
        this.townName = townName;
        this.worldName = worldName;
        this.chunks = new ArrayList<>(chunkKeys);
        this.grouped = (chunkKeys.size() > 1);
        this.groupName = groupName;

        this.type = PlotType.PUBLIC; // Type par défaut (terrain public accessible à tous)
        this.municipalSubType = MunicipalSubType.NONE;
        this.claimDate = LocalDateTime.now();
        this.forSale = false;
        this.forRent = false;
        this.rentEndDate = null; // Pas de location active
        this.companyDebtAmount = 0.0;
        this.debtWarningCount = 0;
        this.particularDebtAmount = 0.0;
        this.particularDebtWarningCount = 0;
        this.protectedBlocks = new HashSet<>();
        this.renterBlockTracker = new RenterBlockTracker();
        this.playerPermissions = new HashMap<>();
        this.trustedPlayers = new HashSet<>();
        this.flags = new EnumMap<>(PlotFlag.class);
        this.ownerAuthorizedPlayers = new HashSet<>();
        this.renterAuthorizedPlayers = new HashSet<>();

        for (PlotFlag flag : PlotFlag.values()) {
            flags.put(flag, flag.getDefaultValue());
        }
    }

    // ========== IMPLÉMENTATION TerritoryEntity ==========

    public String getIdentifier() {
        // Pour les terrains groupés, retourner le nom du groupe
        // Pour les terrains simples, retourner les coordonnées du premier chunk
        if (grouped && groupName != null) {
            return groupName;
        }
        return chunks.isEmpty() ? "unknown" : chunks.get(0);
    }

    // ========== NOUVEAU : Getters/Setters pour multi-chunks ==========

    public List<String> getChunks() { return new ArrayList<>(chunks); }
    public int getChunkCount() { return chunks.size(); }
    public boolean isGrouped() { return grouped; }
    public String getGroupName() { return groupName; }

    public void setGrouped(boolean grouped) {
        this.grouped = grouped;
        if (!grouped) {
            groupName = null;
        }
    }

    public void setGroupName(String name) {
        this.groupName = name;
        this.grouped = (chunks.size() > 1);
    }

    public void addChunk(String chunkKey) {
        if (!chunks.contains(chunkKey)) {
            chunks.add(chunkKey);
            grouped = (chunks.size() > 1);
        }
    }

    public void removeChunk(String chunkKey) {
        chunks.remove(chunkKey);
        grouped = (chunks.size() > 1);
        if (!grouped) {
            groupName = null;
        }
    }

    public boolean containsChunk(String world, int chunkX, int chunkZ) {
        String key = world + ":" + chunkX + ":" + chunkZ;
        return chunks.contains(key);
    }

    /**
     * Retourne les coordonnées du premier chunk (pour compatibilité)
     */
    public int getChunkX() {
        if (chunks.isEmpty()) return 0;
        String[] parts = chunks.get(0).split(":");
        return parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
    }

    public int getChunkZ() {
        if (chunks.isEmpty()) return 0;
        String[] parts = chunks.get(0).split(":");
        return parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
    }

    // ========== Getters (implémentation interface + spécifiques) ==========

    public String getTownName() { return townName; }

    public String getWorldName() { return worldName; }

    public String getPlotNumber() { return plotNumber; }

    public PlotType getType() { return type; }

    public MunicipalSubType getMunicipalSubType() { return municipalSubType; }

    public UUID getOwnerUuid() { return ownerUuid; }

    /**
     * Retourne le nom actuel du propriétaire (résolu dynamiquement via UUID).
     * Si le joueur a changé son pseudo sur Mojang, le nouveau nom sera retourné.
     * Si le terrain n'a pas de propriétaire, retourne "Municipal".
     */
    public String getOwnerName() {
        if (ownerUuid == null && storedOwnerName == null) {
            return "Municipal";
        }
        return PlayerNameResolver.getName(ownerUuid, storedOwnerName);
    }

    /**
     * Retourne le nom stocké en base de données (pour sauvegarde).
     */
    public String getStoredOwnerName() { return storedOwnerName; }

    public String getCompanyName() { return companyName; }

    public String getCompanySiret() { return companySiret; }

    public double getCompanyDebtAmount() { return companyDebtAmount; }

    public LocalDateTime getLastDebtWarningDate() { return lastDebtWarningDate; }

    public int getDebtWarningCount() { return debtWarningCount; }

    // NOUVEAU : Getters pour dettes particuliers
    public double getParticularDebtAmount() { return particularDebtAmount; }

    public LocalDateTime getParticularLastDebtWarningDate() { return particularLastDebtWarningDate; }

    public int getParticularDebtWarningCount() { return particularDebtWarningCount; }

    public double getSalePrice() { return salePrice; }

    public boolean isForSale() { return forSale; }

    public double getRentPricePerDay() { return rentPricePerDay; }

    // FIX BASSE #7: Méthodes getRentPrice() et getRentDurationDays() deprecated supprimées
    // → Utiliser getRentPricePerDay() et getRentDaysRemaining()

    public boolean isForRent() { return forRent; }

    public UUID getRenterUuid() { return renterUuid; }

    /**
     * Retourne le nom actuel du locataire (résolu dynamiquement via UUID).
     * Si le joueur a changé son pseudo sur Mojang, le nouveau nom sera retourné.
     */
    public String getRenterName() {
        return PlayerNameResolver.getName(renterUuid);
    }

    public String getRenterCompanySiret() { return renterCompanySiret; }

    public LocalDateTime getRentStartDate() { return rentStartDate; }

    public LocalDateTime getRentEndDate() { return rentEndDate; }

    /**
     * Calcule le nombre de jours restants (arrondi)
     * Pour compatibilité avec l'ancien code
     * @return Nombre de jours restants (0 si expiré)
     */
    public int getRentDaysRemaining() {
        RentTimeRemaining remaining = getRentTimeRemaining();
        return remaining != null ? remaining.days : 0;
    }

    public LocalDateTime getClaimDate() { return claimDate; }

    public Set<String> getProtectedBlocks() { return new HashSet<>(protectedBlocks); }

    public RenterBlockTracker getRenterBlockTracker() { return renterBlockTracker; }

    // Setters
    public void setType(PlotType type) {
        PlotType oldType = this.type;
        this.type = type;

        // Fire event
        com.gravityyfh.roleplaycity.town.event.PlotTypeChangeEvent event =
            new com.gravityyfh.roleplaycity.town.event.PlotTypeChangeEvent(this, oldType, type);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
    }
    public void setMunicipalSubType(MunicipalSubType subType) { this.municipalSubType = subType; }
    public void setPlotNumber(String plotNumber) { this.plotNumber = plotNumber; }

    public void setOwner(UUID ownerUuid, String ownerName) {
        UUID oldOwnerUuid = this.ownerUuid;

        // 🔑 Si le propriétaire change, nettoyer les autorisations de l'ancien
        if (oldOwnerUuid != null && !oldOwnerUuid.equals(ownerUuid)) {
            clearOwnerAuthorizations();
        }

        this.ownerUuid = ownerUuid;
        this.storedOwnerName = ownerName;

        // Fire event
        com.gravityyfh.roleplaycity.town.event.PlotOwnerChangeEvent event =
            new com.gravityyfh.roleplaycity.town.event.PlotOwnerChangeEvent(this, oldOwnerUuid, ownerUuid, ownerName);
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
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

    public void setClaimDate(LocalDateTime date) {
        this.claimDate = date;
    }

    public void setDebtWarningCount(int count) {
        this.debtWarningCount = count;
    }

    public void resetDebt() {
        this.companyDebtAmount = 0.0;
        this.debtWarningCount = 0;
        this.lastDebtWarningDate = null;
    }

    // NOUVEAU : Setters pour dettes particuliers
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

    public void setSalePrice(double price) {
        this.salePrice = price;
    }

    public void setForSale(boolean forSale) {
        this.forSale = forSale;
    }

    // FIX BASSE #7: Méthode setRent() deprecated supprimée
    // → Utiliser setRentPricePerDay() et setRentDaysRemaining()

    public void setRentPricePerDay(double pricePerDay) {
        this.rentPricePerDay = pricePerDay;
    }

    public void setForRent(boolean forRent) {
        this.forRent = forRent;
    }

    /**
     * Définit un locataire avec une durée de location
     * @param renterUuid UUID du locataire
     * @param initialDays Nombre de jours de location (max 30)
     */
    public void setRenter(UUID renterUuid, int initialDays) {
        this.renterUuid = renterUuid;
        LocalDateTime now = LocalDateTime.now();
        this.rentStartDate = now;
        // Calculer la date d'expiration: maintenant + nombre de jours (max 30)
        int days = Math.min(initialDays, 30);
        this.rentEndDate = now.plusDays(days);
    }

    public void setRenterCompanySiret(String renterCompanySiret) {
        this.renterCompanySiret = renterCompanySiret;
    }

    public void clearRenter() {
        resetDebt();
        resetParticularDebt();

        UUID oldRenter = this.renterUuid;
        String oldRenterSiret = this.renterCompanySiret; // SAUVEGARDER avant de clear

        // 🔑 NETTOYER les autorisations du locataire AVANT de le supprimer
        clearRenterAuthorizations();

        this.renterUuid = null;
        this.renterCompanySiret = null;
        this.rentStartDate = null;
        this.rentEndDate = null; // Pas de location active
        this.protectedBlocks.clear();

        // NOUVEAU : Nettoyer le tracker des blocs du locataire
        if (oldRenter != null && renterBlockTracker != null) {
            renterBlockTracker.clearRenter(oldRenter);
        }

        // Supprimer les shops du locataire sur ce terrain
        if (oldRenterSiret != null) {
            com.gravityyfh.roleplaycity.RoleplayCity plugin =
                (com.gravityyfh.roleplaycity.RoleplayCity) org.bukkit.Bukkit.getPluginManager().getPlugin("RoleplayCity");
            if (plugin != null && plugin.getShopManager() != null) {
                int deleted = plugin.getShopManager().deleteShopsByCompanyOnPlot(
                    oldRenterSiret,
                    this,
                    "Fin de la location du terrain"
                );
                if (deleted > 0) {
                    plugin.getLogger().info(String.format(
                        "[Plot] %d boutique(s) du locataire (SIRET: %s) supprimée(s) sur le terrain %s:%d,%d",
                        deleted, oldRenterSiret, this.worldName, this.getChunkX(), this.getChunkZ()
                    ));
                }
            }
        }
    }

    /**
     * Recharge la location en ajoutant des jours
     * Inspiré du système d'AbonnementConnection
     * @param daysToAdd Nombre de jours à ajouter
     * @return Nombre de jours effectivement ajoutés (limité par la limite de 30 jours)
     */
    public int rechargeDays(int daysToAdd) {
        if (daysToAdd <= 0 || renterUuid == null) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseTime;

        // Si la location est encore valide, ajouter à partir de rentEndDate
        // Sinon, ajouter à partir de maintenant
        if (rentEndDate != null && rentEndDate.isAfter(now)) {
            baseTime = rentEndDate;
        } else {
            baseTime = now;
        }

        // Calculer la nouvelle date de fin
        LocalDateTime newEndDate = baseTime.plusDays(daysToAdd);

        // Vérifier la limite de 30 jours à partir de maintenant
        LocalDateTime maxEndDate = now.plusDays(30);

        if (newEndDate.isAfter(maxEndDate)) {
            // Limiter à 30 jours max à partir de maintenant
            this.rentEndDate = maxEndDate;
            // Calculer combien de jours ont réellement été ajoutés
            return (int) java.time.Duration.between(baseTime, maxEndDate).toDays();
        } else {
            this.rentEndDate = newEndDate;
            return daysToAdd;
        }
    }

    /**
     * Vérifie si la location est expirée et nettoie le locataire si nécessaire
     * À appeler périodiquement (toutes les 5 minutes par exemple)
     */
    public void checkRentExpiration() {
        if (rentEndDate == null || renterUuid == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(rentEndDate)) {
            // Location expirée
            clearRenter();
        }
    }

    /**
     * 📅 Calcule la durée restante précise de la location
     * Retourne un objet contenant jours, heures et minutes restants
     * Système basé sur la date d'expiration (comme AbonnementConnection)
     *
     * @return RentTimeRemaining avec durée détaillée, ou null si pas de location
     */
    public RentTimeRemaining getRentTimeRemaining() {
        if (renterUuid == null || rentEndDate == null) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now();

        // Si on a dépassé la date d'expiration, la location est expirée
        if (now.isAfter(rentEndDate)) {
            return new RentTimeRemaining(0, 0, 0);
        }

        // Calculer la différence précise jusqu'à rentEndDate
        long totalMinutes = java.time.Duration.between(now, rentEndDate).toMinutes();

        int days = (int) (totalMinutes / (24 * 60));
        int hours = (int) ((totalMinutes % (24 * 60)) / 60);
        int minutes = (int) (totalMinutes % 60);

        return new RentTimeRemaining(days, hours, minutes);
    }

    /**
     * 📅 Calcule le temps restant avant saisie pour dette d'entreprise (PROFESSIONNEL)
     * Délai de 7 jours depuis la date d'avertissement
     *
     * @return DebtTimeRemaining avec durée détaillée, ou null si pas de dette
     */
    public DebtTimeRemaining getCompanyDebtTimeRemaining() {
        if (companyDebtAmount <= 0 || lastDebtWarningDate == null) {
            return null;
        }
        return calculateDebtTimeRemaining(lastDebtWarningDate);
    }

    /**
     * 📅 Calcule le temps restant avant saisie pour dette de particulier
     * Délai de 7 jours depuis la date d'avertissement
     *
     * @return DebtTimeRemaining avec durée détaillée, ou null si pas de dette
     */
    public DebtTimeRemaining getParticularDebtTimeRemaining() {
        if (particularDebtAmount <= 0 || particularLastDebtWarningDate == null) {
            return null;
        }
        return calculateDebtTimeRemaining(particularLastDebtWarningDate);
    }

    /**
     * Calcule le temps restant avant saisie (7 jours de délai depuis avertissement)
     * Méthode privée utilisée par getCompanyDebtTimeRemaining() et getParticularDebtTimeRemaining()
     */
    private DebtTimeRemaining calculateDebtTimeRemaining(LocalDateTime warningDate) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = warningDate.plusDays(7); // 7 jours de délai pour payer

        // Si on a dépassé le délai, saisie imminente
        if (now.isAfter(deadline)) {
            return new DebtTimeRemaining(0, 0, 0);
        }

        // Calculer la différence précise jusqu'à la deadline
        long totalMinutes = java.time.Duration.between(now, deadline).toMinutes();

        int days = (int) (totalMinutes / (24 * 60));
        int hours = (int) ((totalMinutes % (24 * 60)) / 60);
        int minutes = (int) (totalMinutes % 60);

        return new DebtTimeRemaining(days, hours, minutes);
    }

    /**
         * Classe interne pour représenter le temps restant d'une location
         */
        public record RentTimeRemaining(int days, int hours, int minutes) {

        /**
             * Formate la durée en format compact pour scoreboard
             * Exemples: "5j 3h", "2j 12h 30m", "18h 45m", "30m"
             */
            public String formatCompact() {
                if (days > 0) {
                    if (hours > 0) {
                        if (minutes > 0) {
                            return days + "j " + hours + "h " + minutes + "m";
                        }
                        return days + "j " + hours + "h";
                    }
                    return days + "j";
                } else if (hours > 0) {
                    if (minutes > 0) {
                        return hours + "h " + minutes + "m";
                    }
                    return hours + "h";
                } else {
                    return minutes + "m";
                }
            }

            /**
             * Formate la durée en format détaillé pour GUI
             * Exemples: "5 jours, 3 heures", "2 jours, 12 heures, 30 minutes"
             */
            public String formatDetailed() {
                StringBuilder sb = new StringBuilder();

                if (days > 0) {
                    sb.append(days).append(days > 1 ? " jours" : " jour");
                }

                if (hours > 0) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(hours).append(hours > 1 ? " heures" : " heure");
                }

                if (minutes > 0) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(minutes).append(minutes > 1 ? " minutes" : " minute");
                }

                if (sb.length() == 0) {
                    return "Expiré";
                }

                return sb.toString();
            }

            /**
             * Vérifie si la location est expirée
             */
            public boolean isExpired() {
                return days == 0 && hours == 0 && minutes == 0;
            }
        }

    /**
     * Classe interne pour représenter le temps restant avant saisie pour dette
     */
    public record DebtTimeRemaining(int days, int hours, int minutes) {

        /**
         * Formate la durée en format compact pour scoreboard/item lore
         * Exemples: "5j 3h", "2j 12h 30m", "18h 45m", "30m"
         */
        public String formatCompact() {
            if (days > 0) {
                if (hours > 0) {
                    if (minutes > 0) {
                        return days + "j " + hours + "h " + minutes + "m";
                    }
                    return days + "j " + hours + "h";
                }
                return days + "j";
            } else if (hours > 0) {
                if (minutes > 0) {
                    return hours + "h " + minutes + "m";
                }
                return hours + "h";
            } else if (minutes > 0) {
                return minutes + "m";
            } else {
                return "Expiré";
            }
        }

        /**
         * Formate la durée en format détaillé pour GUI/messages
         * Exemples: "5 jours, 3 heures", "2 jours, 12 heures, 30 minutes"
         */
        public String formatDetailed() {
            StringBuilder sb = new StringBuilder();

            if (days > 0) {
                sb.append(days).append(days > 1 ? " jours" : " jour");
            }

            if (hours > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(hours).append(hours > 1 ? " heures" : " heure");
            }

            if (minutes > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(minutes).append(minutes > 1 ? " minutes" : " minute");
            }

            if (sb.length() == 0) {
                return "Expiré";
            }

            return sb.toString();
        }

        /**
         * Vérifie si le délai est expiré (saisie imminente)
         */
        public boolean isExpired() {
            return days == 0 && hours == 0 && minutes == 0;
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

    /**
     * Nettoyer tous les blocs protégés (utilisé lors de la mise en location)
     */
    public void clearProtectedBlocks() {
        protectedBlocks.clear();
    }

    public void clearOwner() {
        UUID oldOwnerUuid = this.ownerUuid;

        // 🔑 NETTOYER les autorisations du propriétaire AVANT de le supprimer
        clearOwnerAuthorizations();

        this.ownerUuid = null;
        this.storedOwnerName = null;
        this.companyName = null;
        this.forSale = false;
        this.forRent = false;
        clearRenter();

        // Fire event
        com.gravityyfh.roleplaycity.town.event.PlotClearEvent event =
            new com.gravityyfh.roleplaycity.town.event.PlotClearEvent(this, oldOwnerUuid, "Manual clear");
        org.bukkit.Bukkit.getPluginManager().callEvent(event);
    }

    // ========== Utility methods (implémentation interface) ==========

    public boolean isOwnedBy(UUID playerUuid) {
        return ownerUuid != null && ownerUuid.equals(playerUuid);
    }

    public boolean isRentedBy(UUID playerUuid) {
        return renterUuid != null && renterUuid.equals(playerUuid);
    }

    // FIX BASSE #7: Méthode isRentExpired() deprecated supprimée
    // → Utiliser getRentDaysRemaining() <= 0

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
        if (type == null) return 0.0;

        // Multiplier la taxe par le nombre de chunks dans ce plot
        int chunkCount = chunks != null ? chunks.size() : 1;
        return type.getDailyTax() * chunkCount;
    }

    public boolean canPlayerBuild(UUID playerUuid, TownRole role) {
        // PRIORITÉ 1: Locataire peut TOUJOURS construire sur son terrain loué
        if (isRentedBy(playerUuid)) {
            return true;
        }

        // PRIORITÉ 2: Joueur autorisé peut construire
        if (hasAuthorization(playerUuid)) {
            return true;
        }

        // Public : espaces communs (routes, places, parcs) - seuls maire/adjoints peuvent aménager
        if (isPublic()) {
            return role == TownRole.MAIRE || role == TownRole.ADJOINT;
        }

        // Municipal : bâtiments administratifs - architecte, maire ou adjoint
        if (isMunicipal()) {
            return role == TownRole.ARCHITECTE || role == TownRole.MAIRE || role == TownRole.ADJOINT;
        }

        // Terrain non-attribué (pas de propriétaire, pas de locataire) : maire et adjoints
        if (ownerUuid == null) {
            return role == TownRole.MAIRE || role == TownRole.ADJOINT;
        }

        // Particulier/Professionnel : propriétaire uniquement (locataire déjà vérifié)
        return isOwnedBy(playerUuid);
    }

    /**
     * Vérifie si un joueur peut construire sur cette parcelle (avec contexte de ville)
     */
    public boolean canBuild(UUID playerUuid, Town town) {
        // Récupérer le rôle du joueur (null si pas membre)
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
        // Public : infrastructures de ville (routes) - interaction réservée aux membres
        if (isPublic()) {
            return town.isMember(playerUuid);
        }

        // Municipal : tous les membres de la ville peuvent interagir
        if (isMunicipal()) {
            return town.isMember(playerUuid);
        }

        // Particulier/Professionnel
        // Si locataire, toujours autorisé
        if (isRentedBy(playerUuid)) {
            return true;
        }

        // Si joueur autorisé (par propriétaire ou locataire), autorisé
        if (hasAuthorization(playerUuid)) {
            return true;
        }

        // Si propriétaire mais terrain loué : restrictions sur les blocs d'accès
        if (isOwnedBy(playerUuid) && renterUuid != null) {
            // Le propriétaire ne peut pas utiliser les blocs d'accès pendant la location
            // Mais il peut ouvrir les conteneurs (coffres, fours, etc.)
            // Note: La vérification du type de bloc se fera dans TownProtectionListener
            return true;
        }

        // Si propriétaire et terrain non loué : autorisé
        if (isOwnedBy(playerUuid)) {
            return true;
        }

        // Les adjoints et le maire peuvent toujours interagir
        TownRole role = town.getMemberRole(playerUuid);
        return role == TownRole.MAIRE || role == TownRole.ADJOINT;
    }

    public String getCoordinates() {
        if (grouped && chunks.size() > 1) {
            return String.format("%s (%d chunks)", groupName != null ? groupName : "Groupe", chunks.size());
        }
        return String.format("(%d, %d)", getChunkX(), getChunkZ());
    }

    public boolean matchesChunk(Chunk chunk) {
        return containsChunk(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    /**
     * Retourne les informations d'affichage pour les joueurs (SANS le nom technique du groupe)
     * Utilisé dans le scoreboard, GUI, etc. pour masquer les noms auto-générés
     */
    public String getDisplayInfo() {
        if (grouped && chunks.size() > 1) {
            // Ne pas afficher le nom technique, juste "Terrain groupé (X chunks)"
            return String.format("Terrain groupé (%d chunks)", chunks.size());
        }
        // Terrain simple : afficher les coordonnées
        return String.format("(%d, %d)", getChunkX(), getChunkZ());
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

    // ========== 🔑 Système d'autorisations parentales ==========

    /**
     * Ajoute un joueur autorisé par le propriétaire
     * @param childUuid UUID du joueur à autoriser
     * @return true si ajouté, false si limite atteinte ou déjà présent
     */
    public boolean addOwnerAuthorizedPlayer(UUID childUuid) {
        if (ownerAuthorizedPlayers.size() >= MAX_AUTHORIZED_PLAYERS) {
            return false;
        }
        return ownerAuthorizedPlayers.add(childUuid);
    }

    /**
     * Ajoute un joueur autorisé par le locataire
     * @param childUuid UUID du joueur à autoriser
     * @return true si ajouté, false si limite atteinte ou déjà présent
     */
    public boolean addRenterAuthorizedPlayer(UUID childUuid) {
        if (renterAuthorizedPlayers.size() >= MAX_AUTHORIZED_PLAYERS) {
            return false;
        }
        return renterAuthorizedPlayers.add(childUuid);
    }

    /**
     * Retire un joueur autorisé par le propriétaire
     */
    public boolean removeOwnerAuthorizedPlayer(UUID childUuid) {
        return ownerAuthorizedPlayers.remove(childUuid);
    }

    /**
     * Retire un joueur autorisé par le locataire
     */
    public boolean removeRenterAuthorizedPlayer(UUID childUuid) {
        return renterAuthorizedPlayers.remove(childUuid);
    }

    /**
     * Obtenir tous les joueurs autorisés par le propriétaire
     */
    public Set<UUID> getOwnerAuthorizedPlayers() {
        return new HashSet<>(ownerAuthorizedPlayers);
    }

    /**
     * Obtenir tous les joueurs autorisés par le locataire
     */
    public Set<UUID> getRenterAuthorizedPlayers() {
        return new HashSet<>(renterAuthorizedPlayers);
    }

    /**
     * Vérifie si un joueur est autorisé (par propriétaire OU locataire)
     * @param playerUuid UUID du joueur
     * @return true si le joueur a une autorisation valide
     */
    public boolean hasAuthorization(UUID playerUuid) {
        // Propriétaire ou locataire = toujours autorisé
        if (isOwnedBy(playerUuid) || isRentedBy(playerUuid)) {
            return true;
        }
        // Autorisé par le propriétaire (si propriétaire existe)
        if (ownerUuid != null && ownerAuthorizedPlayers.contains(playerUuid)) {
            return true;
        }
        // Autorisé par le locataire (si locataire existe)
        if (renterUuid != null && renterAuthorizedPlayers.contains(playerUuid)) {
            return true;
        }
        return false;
    }

    /**
     * Vérifie si un joueur est autorisé par le propriétaire
     */
    public boolean isAuthorizedByOwner(UUID playerUuid) {
        return ownerAuthorizedPlayers.contains(playerUuid);
    }

    /**
     * Vérifie si un joueur est autorisé par le locataire
     */
    public boolean isAuthorizedByRenter(UUID playerUuid) {
        return renterAuthorizedPlayers.contains(playerUuid);
    }

    /**
     * Nettoie les autorisations du propriétaire (appelé quand le propriétaire change)
     */
    public void clearOwnerAuthorizations() {
        ownerAuthorizedPlayers.clear();
    }

    /**
     * Nettoie les autorisations du locataire (appelé quand le locataire change)
     */
    public void clearRenterAuthorizations() {
        renterAuthorizedPlayers.clear();
    }

    /**
     * Obtenir le nombre maximum d'autorisations par parent
     */
    public static int getMaxAuthorizedPlayers() {
        return MAX_AUTHORIZED_PLAYERS;
    }

    /**
     * Vérifie si le propriétaire peut encore ajouter des joueurs autorisés
     */
    public boolean canOwnerAddMore() {
        return ownerAuthorizedPlayers.size() < MAX_AUTHORIZED_PLAYERS;
    }

    /**
     * Vérifie si le locataire peut encore ajouter des joueurs autorisés
     */
    public boolean canRenterAddMore() {
        return renterAuthorizedPlayers.size() < MAX_AUTHORIZED_PLAYERS;
    }

    /**
     * Définit les joueurs autorisés par le propriétaire (pour chargement)
     */
    public void setOwnerAuthorizedPlayers(Set<UUID> players) {
        ownerAuthorizedPlayers.clear();
        if (players != null) {
            ownerAuthorizedPlayers.addAll(players);
        }
    }

    /**
     * Définit les joueurs autorisés par le locataire (pour chargement)
     */
    public void setRenterAuthorizedPlayers(Set<UUID> players) {
        renterAuthorizedPlayers.clear();
        if (players != null) {
            renterAuthorizedPlayers.addAll(players);
        }
    }

    /**
     * Définit directement le nombre de jours restants (utilisé pour synchronisation avec groupe)
     * Recalcule rentEndDate à partir de maintenant + days
     */
    public void setRentDaysRemaining(int days) {
        if (days > 0) {
            LocalDateTime now = LocalDateTime.now();
            this.rentEndDate = now.plusDays(days);
        } else {
            this.rentEndDate = null;
        }
    }

    public void setRentStartDate(LocalDateTime rentStartDate) {
        this.rentStartDate = rentStartDate;
    }

    /**
     * Définit directement la date de fin de location
     * @param rentEndDate Nouvelle date de fin
     */
    public void setRentEndDate(LocalDateTime rentEndDate) {
        this.rentEndDate = rentEndDate;
    }

    // ========== Gestion du spawn prison (COMMISSARIAT) ==========

    /**
     * Définit le spawn de prison pour ce COMMISSARIAT
     */
    public void setPrisonSpawn(Location location) {
        this.prisonSpawnLocation = location;
    }

    /**
     * Obtient le spawn de prison
     */
    public Location getPrisonSpawn() {
        return prisonSpawnLocation;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 📬 GESTION DE LA BOÎTE AUX LETTRES
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    /**
     * Vérifie si ce plot a une boîte aux lettres
     */
    public boolean hasMailbox() {
        return mailbox != null;
    }

    /**
     * Obtient la boîte aux lettres de ce plot
     */
    public com.gravityyfh.roleplaycity.postal.data.Mailbox getMailbox() {
        return mailbox;
    }

    /**
     * Définit la boîte aux lettres de ce plot
     */
    public void setMailbox(com.gravityyfh.roleplaycity.postal.data.Mailbox mailbox) {
        this.mailbox = mailbox;
    }

    /**
     * Retire la boîte aux lettres de ce plot
     */
    public void removeMailbox() {
        this.mailbox = null;
    }

    /**
     * Vérifie si un spawn de prison est défini
     */
    public boolean hasPrisonSpawn() {
        return prisonSpawnLocation != null;
    }

    /**
     * Supprime le spawn de prison
     */
    public void removePrisonSpawn() {
        this.prisonSpawnLocation = null;
    }
}
