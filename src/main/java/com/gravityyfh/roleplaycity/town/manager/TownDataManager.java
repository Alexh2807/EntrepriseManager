package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.*;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public class TownDataManager {
    private final RoleplayCity plugin;
    private final File townsFile;
    private FileConfiguration townsConfig;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // FIX CRITIQUE P1.2: Lock pour éviter corruption lors de sauvegardes parallèles
    private final ReentrantLock saveLock = new ReentrantLock();

    // FIX BASSE #12: Dirty flag pour batching des sauvegardes
    private volatile boolean isDirty = false;
    private long lastSaveTime = System.currentTimeMillis();
    private static final long SAVE_INTERVAL_MS = 60000; // Sauvegarder au maximum toutes les 60 secondes

    public TownDataManager(RoleplayCity plugin) {
        this.plugin = plugin;
        this.townsFile = new File(plugin.getDataFolder(), "towns.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!townsFile.exists()) {
            try {
                townsFile.getParentFile().mkdirs();
                townsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer towns.yml: " + e.getMessage());
            }
        }
        townsConfig = YamlConfiguration.loadConfiguration(townsFile);
    }

    public void saveTowns(Map<String, Town> towns) {
        townsConfig = new YamlConfiguration();

        for (Map.Entry<String, Town> entry : towns.entrySet()) {
            String townName = entry.getKey();
            Town town = entry.getValue();
            String path = "towns." + townName;

            // Informations de base
            townsConfig.set(path + ".description", town.getDescription());
            townsConfig.set(path + ".mayor-uuid", town.getMayorUuid().toString());
            townsConfig.set(path + ".foundation-date", town.getFoundationDate().format(DATE_FORMAT));

            // Économie
            townsConfig.set(path + ".bank-balance", town.getBankBalance());
            townsConfig.set(path + ".citizen-tax", town.getCitizenTax());
            townsConfig.set(path + ".company-tax", town.getCompanyTax());
            townsConfig.set(path + ".last-tax-collection", town.getLastTaxCollection().format(DATE_FORMAT));

            // Niveau de la ville
            townsConfig.set(path + ".level", town.getLevel().name());

            // Membres
            int memberIndex = 0;
            for (TownMember member : town.getMembers().values()) {
                String memberPath = path + ".members." + memberIndex;
                townsConfig.set(memberPath + ".uuid", member.getPlayerUuid().toString());
                townsConfig.set(memberPath + ".name", member.getPlayerName());

                // Sauvegarder tous les rôles
                List<String> roleNames = new ArrayList<>();
                for (TownRole role : member.getRoles()) {
                    roleNames.add(role.name());
                }
                townsConfig.set(memberPath + ".roles", roleNames);

                // Garder "role" pour compatibilité (rôle principal)
                townsConfig.set(memberPath + ".role", member.getRole().name());

                townsConfig.set(memberPath + ".join-date", member.getJoinDate().format(DATE_FORMAT));
                townsConfig.set(memberPath + ".last-online", member.getLastOnline().format(DATE_FORMAT));
                memberIndex++;
            }

            // Sauvegarder les invitations en attente
            Map<UUID, LocalDateTime> invitations = town.getPendingInvitations();
            if (!invitations.isEmpty()) {
                int inviteIndex = 0;
                for (Map.Entry<UUID, LocalDateTime> invite : invitations.entrySet()) {
                    String invitePath = path + ".invitations." + inviteIndex;
                    townsConfig.set(invitePath + ".player-uuid", invite.getKey().toString());
                    townsConfig.set(invitePath + ".invite-date", invite.getValue().format(DATE_FORMAT));
                    inviteIndex++;
                }
            }

            // Parcelles (système unifié : 1 ou plusieurs chunks)
            int plotIndex = 0;
            for (Plot plot : town.getPlots().values()) {
                String plotPath = path + ".plots." + plotIndex;
                townsConfig.set(plotPath + ".world", plot.getWorldName());

                // NOUVEAU SYSTÈME UNIFIÉ : Sauvegarder la liste de chunks
                townsConfig.set(plotPath + ".chunks", plot.getChunks());
                townsConfig.set(plotPath + ".grouped", plot.isGrouped());
                if (plot.getGroupName() != null) {
                    townsConfig.set(plotPath + ".group-name", plot.getGroupName());
                }

                townsConfig.set(plotPath + ".type", plot.getType().name());
                townsConfig.set(plotPath + ".municipal-subtype", plot.getMunicipalSubType().name());

                // Sauvegarder le numéro de terrain (si existant)
                if (plot.getPlotNumber() != null) {
                    townsConfig.set(plotPath + ".plot-number", plot.getPlotNumber());
                }

                townsConfig.set(plotPath + ".claim-date", plot.getClaimDate().format(DATE_FORMAT));

                if (plot.getOwnerUuid() != null) {
                    townsConfig.set(plotPath + ".owner-uuid", plot.getOwnerUuid().toString());
                    townsConfig.set(plotPath + ".owner-name", plot.getOwnerName());
                }

                if (plot.getCompanyName() != null) {
                    townsConfig.set(plotPath + ".company", plot.getCompanyName());
                }

                // Sauvegarder le SIRET de l'entreprise (ajouté pour système PRO)
                if (plot.getCompanySiret() != null) {
                    townsConfig.set(plotPath + ".company-siret", plot.getCompanySiret());
                }

                // Sauvegarder les données de dette (système PRO)
                if (plot.getCompanyDebtAmount() > 0) {
                    townsConfig.set(plotPath + ".debt-amount", plot.getCompanyDebtAmount());
                    townsConfig.set(plotPath + ".debt-warning-count", plot.getDebtWarningCount());
                    if (plot.getLastDebtWarningDate() != null) {
                        townsConfig.set(plotPath + ".last-debt-warning", plot.getLastDebtWarningDate().format(DATE_FORMAT));
                    }
                }

                // Sauvegarder les données de dette PARTICULIER
                if (plot.getParticularDebtAmount() > 0) {
                    townsConfig.set(plotPath + ".particular-debt-amount", plot.getParticularDebtAmount());
                    townsConfig.set(plotPath + ".particular-debt-warning-count", plot.getParticularDebtWarningCount());
                    if (plot.getParticularLastDebtWarningDate() != null) {
                        townsConfig.set(plotPath + ".particular-last-debt-warning", plot.getParticularLastDebtWarningDate().format(DATE_FORMAT));
                    }
                }

                if (plot.isForSale()) {
                    townsConfig.set(plotPath + ".for-sale", true);
                    townsConfig.set(plotPath + ".sale-price", plot.getSalePrice());
                }

                if (plot.isForRent()) {
                    townsConfig.set(plotPath + ".for-rent", true);
                    townsConfig.set(plotPath + ".rent-price-per-day", plot.getRentPricePerDay());
                }

                if (plot.getRenterUuid() != null) {
                    townsConfig.set(plotPath + ".renter-uuid", plot.getRenterUuid().toString());
                    townsConfig.set(plotPath + ".rent-start", plot.getRentStartDate().format(DATE_FORMAT));
                    // NOUVEAU : Sauvegarder la date d'expiration au lieu du système ancien
                    if (plot.getRentEndDate() != null) {
                        townsConfig.set(plotPath + ".rent-end-date", plot.getRentEndDate().format(DATE_FORMAT));
                    }
                    // Sauvegarder le prix de location même si forRent = false (important pour le locataire actuel)
                    townsConfig.set(plotPath + ".rent-price-per-day", plot.getRentPricePerDay());
                }

                // Sauvegarder le SIRET du locataire (entreprise)
                if (plot.getRenterCompanySiret() != null) {
                    townsConfig.set(plotPath + ".renter-company-siret", plot.getRenterCompanySiret());
                }

                // Sauvegarder les blocs protégés (format compact: "x:y:z, x:y:z, x:y:z")
                if (!plot.getProtectedBlocks().isEmpty()) {
                    String compactBlocks = String.join(", ", plot.getProtectedBlocks());
                    townsConfig.set(plotPath + ".protected-blocks", compactBlocks);
                }

                // NOUVEAU : Sauvegarder le RenterBlockTracker
                if (plot.getRenterBlockTracker() != null) {
                    Map<UUID, Set<com.gravityyfh.roleplaycity.town.data.RenterBlockTracker.BlockPosition>> renterBlocks =
                        plot.getRenterBlockTracker().getAllBlocks();
                    if (!renterBlocks.isEmpty()) {
                        int renterBlockIndex = 0;
                        for (Map.Entry<UUID, Set<com.gravityyfh.roleplaycity.town.data.RenterBlockTracker.BlockPosition>> renterEntry : renterBlocks.entrySet()) {
                            String renterBlockPath = plotPath + ".renter-blocks." + renterBlockIndex;
                            townsConfig.set(renterBlockPath + ".renter-uuid", renterEntry.getKey().toString());

                            List<String> blockPositions = new ArrayList<>();
                            for (com.gravityyfh.roleplaycity.town.data.RenterBlockTracker.BlockPosition blockPos : renterEntry.getValue()) {
                                blockPositions.add(blockPos.serialize());
                            }
                            townsConfig.set(renterBlockPath + ".blocks", blockPositions);
                            renterBlockIndex++;
                        }
                    }
                }

                // Permissions des joueurs
                Map<UUID, Set<PlotPermission>> playerPerms = plot.getAllPlayerPermissions();
                if (!playerPerms.isEmpty()) {
                    int permIndex = 0;
                    for (Map.Entry<UUID, Set<PlotPermission>> permEntry : playerPerms.entrySet()) {
                        String permPath = plotPath + ".permissions." + permIndex;
                        townsConfig.set(permPath + ".player", permEntry.getKey().toString());
                        List<String> perms = new ArrayList<>();
                        for (PlotPermission perm : permEntry.getValue()) {
                            perms.add(perm.name());
                        }
                        townsConfig.set(permPath + ".perms", perms);
                        permIndex++;
                    }
                }

                // Joueurs de confiance
                Set<UUID> trusted = plot.getTrustedPlayers();
                if (!trusted.isEmpty()) {
                    List<String> trustedList = new ArrayList<>();
                    for (UUID uuid : trusted) {
                        trustedList.add(uuid.toString());
                    }
                    townsConfig.set(plotPath + ".trusted-players", trustedList);
                }

                // Flags
                Map<PlotFlag, Boolean> flags = plot.getAllFlags();
                for (Map.Entry<PlotFlag, Boolean> flagEntry : flags.entrySet()) {
                    townsConfig.set(plotPath + ".flags." + flagEntry.getKey().name(), flagEntry.getValue());
                }

                // ⛓️ Spawn prison (pour COMMISSARIAT)
                if (plot.hasPrisonSpawn()) {
                    Location prisonSpawn = plot.getPrisonSpawn();
                    townsConfig.set(plotPath + ".prison-spawn.world", prisonSpawn.getWorld().getName());
                    townsConfig.set(plotPath + ".prison-spawn.x", prisonSpawn.getX());
                    townsConfig.set(plotPath + ".prison-spawn.y", prisonSpawn.getY());
                    townsConfig.set(plotPath + ".prison-spawn.z", prisonSpawn.getZ());
                    townsConfig.set(plotPath + ".prison-spawn.yaw", prisonSpawn.getYaw());
                    townsConfig.set(plotPath + ".prison-spawn.pitch", prisonSpawn.getPitch());
                }

                // 📬 Boîte aux lettres (intégrée dans le plot)
                if (plot.hasMailbox()) {
                    com.gravityyfh.roleplaycity.postal.data.Mailbox mailbox = plot.getMailbox();
                    String mailboxPath = plotPath + ".mailbox";

                    townsConfig.set(mailboxPath + ".type", mailbox.getType().name());

                    // Sauvegarder la position de la tête
                    Location headLoc = mailbox.getHeadLocation();
                    townsConfig.set(mailboxPath + ".head-location.world", headLoc.getWorld().getName());
                    townsConfig.set(mailboxPath + ".head-location.x", headLoc.getX());
                    townsConfig.set(mailboxPath + ".head-location.y", headLoc.getY());
                    townsConfig.set(mailboxPath + ".head-location.z", headLoc.getZ());

                    // Sauvegarder l'inventaire virtuel (items)
                    Map<Integer, org.bukkit.inventory.ItemStack> items = mailbox.getItems();
                    if (!items.isEmpty()) {
                        for (Map.Entry<Integer, org.bukkit.inventory.ItemStack> itemEntry : items.entrySet()) {
                            townsConfig.set(mailboxPath + ".items." + itemEntry.getKey(), itemEntry.getValue());
                        }
                    }
                }

                plotIndex++;
            }
        }

        // FIX CRITIQUE P1.2: Sauvegardes atomiques avec lock
        saveLock.lock();
        try {
            // Étape 1: Écrire dans un fichier temporaire
            File tempFile = new File(townsFile.getParentFile(), "towns.yml.tmp");
            townsConfig.save(tempFile);

            // Étape 2: Renommage atomique (remplace l'ancien fichier)
            Files.move(tempFile.toPath(), townsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            plugin.getLogger().info("Sauvegardé " + towns.size() + " villes dans towns.yml (atomic write)");
        } catch (IOException e) {
            // FIX BASSE: Utiliser logging avec exception complète
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde des villes", e);
        } finally {
            saveLock.unlock();
            // Réinitialiser le dirty flag après sauvegarde réussie
            isDirty = false;
            lastSaveTime = System.currentTimeMillis();
        }
    }

    /**
     * FIX BASSE #12: Marque les données comme modifiées (dirty)
     * La sauvegarde sera effectuée lors du prochain appel à saveIfDirty()
     */
    public void markDirty() {
        isDirty = true;
    }

    /**
     * FIX BASSE #12: Sauvegarde uniquement si les données sont dirty ET que l'intervalle est dépassé
     * Cela permet de batching les sauvegardes pour améliorer les performances
     *
     * @param towns Map des villes à sauvegarder
     * @param force Si true, force la sauvegarde même si l'intervalle n'est pas dépassé
     */
    public void saveIfDirty(Map<String, Town> towns, boolean force) {
        if (!isDirty) {
            return; // Aucune modification, pas besoin de sauvegarder
        }

        long currentTime = System.currentTimeMillis();
        long timeSinceLastSave = currentTime - lastSaveTime;

        if (force || timeSinceLastSave >= SAVE_INTERVAL_MS) {
            plugin.getLogger().fine("[TownDataManager] Sauvegarde (dirty=" + isDirty +
                ", elapsed=" + (timeSinceLastSave / 1000) + "s, force=" + force + ")");
            saveTowns(towns);
        }
    }

    /**
     * FIX BASSE #12: Version simplifiée sans force
     */
    public void saveIfDirty(Map<String, Town> towns) {
        saveIfDirty(towns, false);
    }

    /**
     * Vérifie si les données sont dirty (modifiées mais pas sauvegardées)
     */
    public boolean isDirty() {
        return isDirty;
    }

    /**
     * Retourne le temps écoulé depuis la dernière sauvegarde en millisecondes
     */
    public long getTimeSinceLastSave() {
        return System.currentTimeMillis() - lastSaveTime;
    }

    public Map<String, Town> loadTowns() {
        Map<String, Town> towns = new HashMap<>();
        loadConfig();

        ConfigurationSection townsSection = townsConfig.getConfigurationSection("towns");
        if (townsSection == null) {
            plugin.getLogger().info("Aucune ville à charger.");
            return towns;
        }

        for (String townName : townsSection.getKeys(false)) {
            try {
                // FIX BASSE #31: Valider le nom de la ville au chargement
                if (!plugin.getNameValidator().isValidLoadedName(townName, "ville")) {
                    plugin.getLogger().warning("⚠ Ville avec nom invalide ignorée: " + townName);
                    continue;
                }

                Town town = loadTown(townName, townsSection.getConfigurationSection(townName));
                if (town != null) {
                    towns.put(townName, town);
                }
            } catch (Exception e) {
                // FIX BASSE: Utiliser logging avec exception complète
                plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement de la ville " + townName, e);
            }
        }

        plugin.getLogger().info("Chargé " + towns.size() + " villes depuis towns.yml");
        return towns;
    }

    private Town loadTown(String townName, ConfigurationSection section) {
        if (section == null) return null;

        // Charger les infos de base
        UUID mayorUuid = UUID.fromString(section.getString("mayor-uuid"));
        String description = section.getString("description", "");
        LocalDateTime foundationDate = LocalDateTime.parse(
            section.getString("foundation-date"), DATE_FORMAT);

        // Note: On ne peut pas utiliser le constructeur normal car il initialise des valeurs
        // On doit créer la ville puis charger les données
        // Pour simplifier, on va recréer via reflection ou accepter la limitation

        // Création basique (on aura besoin du nom du maire, on le trouvera dans les membres)
        ConfigurationSection membersSection = section.getConfigurationSection("members");
        String mayorName = "Unknown";
        if (membersSection != null) {
            for (String key : membersSection.getKeys(false)) {
                UUID uuid = UUID.fromString(membersSection.getString(key + ".uuid"));
                if (uuid.equals(mayorUuid)) {
                    mayorName = membersSection.getString(key + ".name");
                    break;
                }
            }
        }

        Town town = new Town(townName, mayorUuid, mayorName);

        // Charger l'économie
        town.setDescription(description);
        double bankBalance = section.getDouble("bank-balance", 0.0);
        if (bankBalance > 0) {
            town.deposit(bankBalance);
        }
        town.setCitizenTax(section.getDouble("citizen-tax", 0.0));
        town.setCompanyTax(section.getDouble("company-tax", 0.0));

        if (section.contains("last-tax-collection")) {
            town.setLastTaxCollection(LocalDateTime.parse(
                section.getString("last-tax-collection"), DATE_FORMAT));
        }

        // Charger le niveau de la ville
        if (section.contains("level")) {
            String levelName = section.getString("level");
            TownLevel level = TownLevel.fromName(levelName);
            if (level != null) {
                town.setLevel(level);
            } else {
                plugin.getLogger().warning("Niveau de ville invalide pour " + townName + ": " + levelName + ". Utilisation du niveau par défaut CAMPEMENT.");
                town.setLevel(TownLevel.CAMPEMENT);
            }
        } else {
            // Rétrocompatibilité : si le niveau n'est pas défini, on met CAMPEMENT
            town.setLevel(TownLevel.CAMPEMENT);
        }

        // Charger les membres (sauf le maire déjà ajouté)
        if (membersSection != null) {
            for (String key : membersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(membersSection.getString(key + ".uuid"));
                    if (uuid.equals(mayorUuid)) continue; // Maire déjà ajouté

                    String name = membersSection.getString(key + ".name");

                    // Charger tous les rôles (nouveau système multi-rôles)
                    List<String> roleNames = membersSection.getStringList(key + ".roles");
                    if (roleNames != null && !roleNames.isEmpty()) {
                        // Système multi-rôles
                        Set<TownRole> roles = new HashSet<>();
                        for (String roleName : roleNames) {
                            try {
                                roles.add(TownRole.valueOf(roleName));
                            } catch (IllegalArgumentException e) {
                                plugin.getLogger().warning("Rôle invalide: " + roleName);
                            }
                        }
                        if (!roles.isEmpty()) {
                            // Ajouter le membre avec le rôle principal, puis ajouter les autres rôles
                            TownRole primaryRole = roles.stream()
                                    .max((r1, r2) -> Integer.compare(r1.getPower(), r2.getPower()))
                                    .orElse(TownRole.CITOYEN);
                            town.addMember(uuid, name, primaryRole);

                            // Ajouter les autres rôles
                            TownMember member = town.getMember(uuid);
                            if (member != null) {
                                member.setRoles(roles);
                            }
                        } else {
                            // Fallback si aucun rôle valide
                            town.addMember(uuid, name, TownRole.CITOYEN);
                        }
                    } else {
                        // Ancien système (un seul rôle) - pour rétrocompatibilité
                        TownRole role = TownRole.valueOf(membersSection.getString(key + ".role", "CITOYEN"));
                        town.addMember(uuid, name, role);
                    }
                } catch (IllegalArgumentException e) {
                    // UUID invalide ou autre erreur
                    String uuidStr = membersSection.getString(key + ".uuid", "inconnu");
                    String name = membersSection.getString(key + ".name", "inconnu");
                    plugin.getLogger().severe("❌ ERREUR: Impossible de charger le membre '" + name + "' de la ville " + townName);
                    plugin.getLogger().severe("   UUID invalide: " + uuidStr);
                    plugin.getLogger().severe("   Le membre a été ignoré. Vérifiez le fichier towns.yml !");
                }
            }
        }

        // Charger les invitations en attente
        ConfigurationSection invitationsSection = section.getConfigurationSection("invitations");
        if (invitationsSection != null) {
            for (String key : invitationsSection.getKeys(false)) {
                try {
                    UUID playerUuid = UUID.fromString(invitationsSection.getString(key + ".player-uuid"));
                    LocalDateTime inviteDate = LocalDateTime.parse(
                        invitationsSection.getString(key + ".invite-date"), DATE_FORMAT);
                    town.invitePlayer(playerUuid, inviteDate);
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement d'une invitation pour " + townName + ": " + e.getMessage());
                }
            }
        }

        // Charger les parcelles (système unifié)
        ConfigurationSection plotsSection = section.getConfigurationSection("plots");
        if (plotsSection != null) {
            for (String key : plotsSection.getKeys(false)) {
                Plot plot = loadPlot(townName, plotsSection.getConfigurationSection(key));
                if (plot != null) {
                    town.addPlot(plot);
                }
            }
        }

        return town;
    }

    private Plot loadPlot(String townName, ConfigurationSection section) {
        if (section == null) return null;

        String worldName = section.getString("world");

        // NOUVEAU SYSTÈME UNIFIÉ : Charger la liste de chunks
        List<String> chunkKeys = section.getStringList("chunks");
        boolean grouped = section.getBoolean("grouped", false);
        String groupName = section.getString("group-name");

        // Date de claim
        LocalDateTime claimDate = null;
        if (section.contains("claim-date")) {
            try {
                claimDate = LocalDateTime.parse(section.getString("claim-date"), DATE_FORMAT);
            } catch (Exception e) {
                plugin.getLogger().warning("Impossible de lire claim-date pour une parcelle de " + townName + ": " + e.getMessage());
            }
        }

        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        Plot plot;

        if (world != null) {
            // Cas 1: Nouveau format avec liste de chunks
            if (!chunkKeys.isEmpty()) {
                if (grouped) {
                    // Terrain groupé (multi-chunks)
                    plot = new Plot(townName, worldName, chunkKeys, groupName);
                } else {
                    // Terrain simple (1 chunk) mais au nouveau format
                    String firstChunk = chunkKeys.get(0);
                    String[] parts = firstChunk.split(":");
                    if (parts.length >= 3) {
                        int chunkX = Integer.parseInt(parts[1]);
                        int chunkZ = Integer.parseInt(parts[2]);
                        plot = new Plot(townName, world.getChunkAt(chunkX, chunkZ));
                    } else {
                        plugin.getLogger().warning("Format de chunk invalide: " + firstChunk);
                        return null;
                    }
                }
            }
            // Cas 2: Ancien format avec chunk-x et chunk-z (rétrocompatibilité)
            else if (section.contains("chunk-x") && section.contains("chunk-z")) {
                int chunkX = section.getInt("chunk-x");
                int chunkZ = section.getInt("chunk-z");
                plot = new Plot(townName, world.getChunkAt(chunkX, chunkZ));
            } else {
                plugin.getLogger().warning("Aucune donnée de chunk trouvée pour une parcelle de " + townName);
                return null;
            }

            if (claimDate != null) {
                plot.setClaimDate(claimDate);
            }
        } else {
            plugin.getLogger().warning("Monde introuvable pour la parcelle: " + worldName + ". Parcelle ignorée.");
            return null;
        }

        // Charger le type et sous-type
        plot.setType(PlotType.valueOf(section.getString("type", "PUBLIC")));
        plot.setMunicipalSubType(MunicipalSubType.valueOf(
            section.getString("municipal-subtype", "NONE")));

        // Charger le numéro de terrain (si existant)
        if (section.contains("plot-number")) {
            plot.setPlotNumber(section.getString("plot-number"));
        }

        // Charger le propriétaire
        if (section.contains("owner-uuid")) {
            UUID ownerUuid = UUID.fromString(section.getString("owner-uuid"));
            String ownerName = section.getString("owner-name");
            plot.setOwner(ownerUuid, ownerName);
        }

        // Charger l'entreprise
        if (section.contains("company")) {
            plot.setCompany(section.getString("company"));
        }

        // Charger le SIRET de l'entreprise (système PRO)
        if (section.contains("company-siret")) {
            plot.setCompanySiret(section.getString("company-siret"));
        }

        // Charger les données de dette (système PRO)
        if (section.contains("debt-amount")) {
            plot.setCompanyDebtAmount(section.getDouble("debt-amount"));
            plot.setDebtWarningCount(section.getInt("debt-warning-count", 0));
            if (section.contains("last-debt-warning")) {
                try {
                    LocalDateTime debtWarningDate = LocalDateTime.parse(
                        section.getString("last-debt-warning"), DATE_FORMAT);
                    plot.setLastDebtWarningDate(debtWarningDate);
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement de last-debt-warning pour parcelle");
                }
            }
        }

        // Charger les données de dette PARTICULIER
        if (section.contains("particular-debt-amount")) {
            plot.setParticularDebtAmount(section.getDouble("particular-debt-amount"));
            plot.setParticularDebtWarningCount(section.getInt("particular-debt-warning-count", 0));
            if (section.contains("particular-last-debt-warning")) {
                try {
                    LocalDateTime particularDebtWarningDate = LocalDateTime.parse(
                        section.getString("particular-last-debt-warning"), DATE_FORMAT);
                    plot.setParticularLastDebtWarningDate(particularDebtWarningDate);
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement de particular-last-debt-warning pour parcelle");
                }
            }
        }

        // Charger vente
        if (section.getBoolean("for-sale", false)) {
            plot.setSalePrice(section.getDouble("sale-price"));
            plot.setForSale(true);
        }

        // Charger location
        if (section.getBoolean("for-rent", false)) {
            // Nouveau système: prix par jour
            if (section.contains("rent-price-per-day")) {
                plot.setRentPricePerDay(section.getDouble("rent-price-per-day"));
            } else if (section.contains("rent-price")) {
                // Ancien système: convertir prix total en prix par jour
                double totalPrice = section.getDouble("rent-price");
                int duration = section.getInt("rent-duration", 1);
                plot.setRentPricePerDay(totalPrice / Math.max(1, duration));
            }
            plot.setForRent(true);
        }

        // Charger locataire
        if (section.contains("renter-uuid")) {
            UUID renterUuid = UUID.fromString(section.getString("renter-uuid"));

            // NOUVEAU SYSTÈME : Charger la date d'expiration directement
            if (section.contains("rent-end-date")) {
                try {
                    LocalDateTime rentEndDate = LocalDateTime.parse(
                        section.getString("rent-end-date"), DATE_FORMAT);

                    // Définir le locataire (initialisation de base)
                    plot.setRenter(renterUuid, 1);
                    // Puis surcharger avec la vraie date d'expiration
                    plot.setRentEndDate(rentEndDate);
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement de rent-end-date, utilisation du fallback: " + e.getMessage());
                    // Fallback: utiliser l'ancien système si disponible
                    int daysRemaining = section.getInt("rent-days-remaining", 1);
                    plot.setRenter(renterUuid, daysRemaining);
                }
            } else {
                // ANCIEN SYSTÈME (rétrocompatibilité) : Calculer rentEndDate depuis lastRentUpdate + rentDaysRemaining
                try {
                    if (section.contains("last-rent-update") && section.contains("rent-days-remaining")) {
                        LocalDateTime lastUpdate = LocalDateTime.parse(
                            section.getString("last-rent-update"), DATE_FORMAT);
                        int daysRemaining = section.getInt("rent-days-remaining", 1);

                        // Calculer la date d'expiration: lastUpdate + daysRemaining jours
                        LocalDateTime rentEndDate = lastUpdate.plusDays(daysRemaining);

                        // Définir le locataire (initialisation de base)
                        plot.setRenter(renterUuid, 1);
                        // Puis surcharger avec la date calculée
                        plot.setRentEndDate(rentEndDate);

                        plugin.getLogger().info("Migration ancien système → nouveau système pour plot: " +
                            "rentEndDate calculée = " + rentEndDate.format(DATE_FORMAT));
                    } else {
                        // Dernier fallback: utiliser rent-days-remaining seulement
                        int daysRemaining = section.getInt("rent-days-remaining", 1);
                        plot.setRenter(renterUuid, daysRemaining);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors de la migration de l'ancien système: " + e.getMessage());
                    // Fallback absolu
                    int daysRemaining = section.getInt("rent-days-remaining", 1);
                    plot.setRenter(renterUuid, daysRemaining);
                }
            }

            // Charger le prix de location (important pour affichage au locataire)
            if (section.contains("rent-price-per-day")) {
                plot.setRentPricePerDay(section.getDouble("rent-price-per-day"));
            } else if (section.contains("rent-price")) {
                // Ancien système: convertir prix total en prix par jour
                double totalPrice = section.getDouble("rent-price");
                int duration = section.getInt("rent-duration", 1);
                plot.setRentPricePerDay(totalPrice / Math.max(1, duration));
            }
        }

        // Charger le SIRET du locataire (entreprise)
        if (section.contains("renter-company-siret")) {
            plot.setRenterCompanySiret(section.getString("renter-company-siret"));
        }

        // ⚠️ MIGRATION AUTOMATIQUE : Si location PRO sans SIRET, le reconstruire
        if (plot.getType() == PlotType.PROFESSIONNEL &&
            plot.getRenterUuid() != null &&
            plot.getRenterCompanySiret() == null) {

            // Tentative de reconstruction du SIRET manquant
            try {
                UUID renterUuid = plot.getRenterUuid();
                Player renterPlayer = plugin.getServer().getPlayer(renterUuid);
                String renterName = renterPlayer != null ? renterPlayer.getName() :
                                  plugin.getServer().getOfflinePlayer(renterUuid).getName();

                if (renterName != null) {
                    // Rechercher l'entreprise du locataire par son nom
                    String companyName = plugin.getEntrepriseManagerLogic().getNomEntrepriseDuMembre(renterName);

                    if (companyName != null) {
                        EntrepriseManagerLogic.Entreprise renterCompany =
                            plugin.getEntrepriseManagerLogic().getEntreprise(companyName);

                        if (renterCompany != null) {
                            plot.setRenterCompanySiret(renterCompany.getSiret());
                            plugin.getLogger().info("Migration: SIRET locataire reconstruit pour le terrain " +
                                plot.getPlotNumber() + " - Entreprise: " + renterCompany.getNom());
                        }
                    } else {
                        plugin.getLogger().warning("Migration: Impossible de retrouver l'entreprise du locataire " +
                            renterName + " pour le terrain " + plot.getPlotNumber());
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors de la migration du SIRET locataire pour " +
                    plot.getPlotNumber() + ": " + e.getMessage());
            }
        }

        // Charger les blocs protégés uniquement si le monde est disponible
        if (world != null) {
            // Support des deux formats: compact (string) et ancien (liste)
            List<String> protectedBlocksList;

            if (section.isString("protected-blocks")) {
                // Nouveau format compact: "x:y:z, x:y:z, x:y:z"
                String compactBlocks = section.getString("protected-blocks", "");
                if (!compactBlocks.isEmpty()) {
                    protectedBlocksList = Arrays.asList(compactBlocks.split(", "));
                } else {
                    protectedBlocksList = new ArrayList<>();
                }
            } else {
                // Ancien format: liste YAML
                protectedBlocksList = section.getStringList("protected-blocks");
            }

            if (!protectedBlocksList.isEmpty()) {
                for (String blockKey : protectedBlocksList) {
                    try {
                        String[] parts = blockKey.split(":");
                        if (parts.length == 3) {
                            Location loc = new Location(
                                world,
                                Integer.parseInt(parts[0]),
                                Integer.parseInt(parts[1]),
                                Integer.parseInt(parts[2])
                            );
                            plot.addProtectedBlock(loc);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erreur lors du chargement d'un bloc protégé: " + blockKey);
                    }
                }
            }
        }
        // NOUVEAU : Charger le RenterBlockTracker
        ConfigurationSection renterBlocksSection = section.getConfigurationSection("renter-blocks");
        if (renterBlocksSection != null) {
            Map<UUID, Set<com.gravityyfh.roleplaycity.town.data.RenterBlockTracker.BlockPosition>> renterBlocks = new HashMap<>();
            for (String key : renterBlocksSection.getKeys(false)) {
                ConfigurationSection renterBlockSection = renterBlocksSection.getConfigurationSection(key);
                if (renterBlockSection != null) {
                    try {
                        UUID renterUuid = UUID.fromString(renterBlockSection.getString("renter-uuid"));
                        List<String> blocksList = renterBlockSection.getStringList("blocks");

                        Set<com.gravityyfh.roleplaycity.town.data.RenterBlockTracker.BlockPosition> blocks = new HashSet<>();
                        for (String blockStr : blocksList) {
                            com.gravityyfh.roleplaycity.town.data.RenterBlockTracker.BlockPosition blockPos =
                                com.gravityyfh.roleplaycity.town.data.RenterBlockTracker.BlockPosition.deserialize(blockStr);
                            if (blockPos != null) {
                                blocks.add(blockPos);
                            }
                        }

                        if (!blocks.isEmpty()) {
                            renterBlocks.put(renterUuid, blocks);
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("Erreur lors du chargement des blocs locataire: " + e.getMessage());
                    }
                }
            }
            if (!renterBlocks.isEmpty()) {
                plot.getRenterBlockTracker().loadBlocks(renterBlocks);
            }
        }

        // Charger les permissions
        ConfigurationSection permsSection = section.getConfigurationSection("permissions");
        if (permsSection != null) {
            for (String key : permsSection.getKeys(false)) {
                ConfigurationSection permSection = permsSection.getConfigurationSection(key);
                if (permSection != null) {
                    UUID playerUuid = UUID.fromString(permSection.getString("player"));
                    List<String> permsList = permSection.getStringList("perms");
                    Set<PlotPermission> permissions = EnumSet.noneOf(PlotPermission.class);
                    for (String permName : permsList) {
                        try {
                            permissions.add(PlotPermission.valueOf(permName));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Permission invalide: " + permName);
                        }
                    }
                    plot.setPlayerPermissions(playerUuid, permissions);
                }
            }
        }

        // Charger les joueurs de confiance
        List<String> trustedList = section.getStringList("trusted-players");
        for (String uuidStr : trustedList) {
            try {
                plot.addTrustedPlayer(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("UUID invalide pour joueur de confiance: " + uuidStr);
            }
        }

        // Charger les flags
        ConfigurationSection flagsSection = section.getConfigurationSection("flags");
        if (flagsSection != null) {
            for (String flagName : flagsSection.getKeys(false)) {
                try {
                    PlotFlag flag = PlotFlag.valueOf(flagName);
                    boolean value = flagsSection.getBoolean(flagName);
                    plot.setFlag(flag, value);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Flag invalide: " + flagName);
                }
            }
        }

        // ⛓️ Charger le spawn prison (pour COMMISSARIAT)
        if (section.contains("prison-spawn")) {
            try {
                String prisonWorldName = section.getString("prison-spawn.world");
                org.bukkit.World prisonWorld = plugin.getServer().getWorld(prisonWorldName);

                if (prisonWorld != null) {
                    double x = section.getDouble("prison-spawn.x");
                    double y = section.getDouble("prison-spawn.y");
                    double z = section.getDouble("prison-spawn.z");
                    float yaw = (float) section.getDouble("prison-spawn.yaw", 0.0);
                    float pitch = (float) section.getDouble("prison-spawn.pitch", 0.0);

                    Location prisonSpawn = new Location(prisonWorld, x, y, z, yaw, pitch);
                    plot.setPrisonSpawn(prisonSpawn);
                } else {
                    plugin.getLogger().warning("Monde introuvable pour prison spawn: " + prisonWorldName);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du chargement du prison spawn: " + e.getMessage());
            }
        }

        // 📬 Charger la boîte aux lettres (intégrée dans le plot)
        if (section.contains("mailbox")) {
            try {
                ConfigurationSection mailboxSection = section.getConfigurationSection("mailbox");
                if (mailboxSection != null) {
                    // Charger le type
                    com.gravityyfh.roleplaycity.postal.data.MailboxType type =
                        com.gravityyfh.roleplaycity.postal.data.MailboxType.valueOf(
                            mailboxSection.getString("type", "LIGHT_GRAY")
                        );

                    // Charger la position de la tête
                    String headWorldName = mailboxSection.getString("head-location.world");
                    org.bukkit.World headWorld = plugin.getServer().getWorld(headWorldName);

                    if (headWorld != null) {
                        double x = mailboxSection.getDouble("head-location.x");
                        double y = mailboxSection.getDouble("head-location.y");
                        double z = mailboxSection.getDouble("head-location.z");
                        Location headLocation = new Location(headWorld, x, y, z);

                        // Charger l'inventaire virtuel (items)
                        Map<Integer, org.bukkit.inventory.ItemStack> items = new HashMap<>();
                        ConfigurationSection itemsSection = mailboxSection.getConfigurationSection("items");
                        if (itemsSection != null) {
                            for (String slotKey : itemsSection.getKeys(false)) {
                                try {
                                    int slot = Integer.parseInt(slotKey);
                                    org.bukkit.inventory.ItemStack item = itemsSection.getItemStack(slotKey);
                                    if (item != null) {
                                        items.put(slot, item);
                                    }
                                } catch (NumberFormatException e) {
                                    plugin.getLogger().warning("Slot invalide dans mailbox: " + slotKey);
                                }
                            }
                        }

                        // Créer l'objet Mailbox et l'attacher au plot
                        com.gravityyfh.roleplaycity.postal.data.Mailbox mailbox =
                            new com.gravityyfh.roleplaycity.postal.data.Mailbox(type, headLocation, items);
                        plot.setMailbox(mailbox);
                    } else {
                        plugin.getLogger().warning("Monde introuvable pour mailbox: " + headWorldName);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur lors du chargement de la mailbox: " + e.getMessage());
            }
        }

        return plot;
    }
}









