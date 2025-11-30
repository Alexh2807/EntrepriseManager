package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.entreprise.model.*;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.*;
import com.gravityyfh.roleplaycity.town.service.TownPersistenceService;
import org.bukkit.Location;
import org.bukkit.World;
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
    private final TownPersistenceService persistenceService;
    private final File townsFile;
    private FileConfiguration townsConfig;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    // Dirty flag pour batching des sauvegardes
    private volatile boolean isDirty = false;
    private long lastSaveTime = System.currentTimeMillis();
    private static final long SAVE_INTERVAL_MS = 60000; // Sauvegarder au maximum toutes les 60 secondes

    public TownDataManager(RoleplayCity plugin, TownPersistenceService persistenceService) {
        this.plugin = plugin;
        this.persistenceService = persistenceService;
        this.townsFile = new File(plugin.getDataFolder(), "towns.yml");
    }

    /**
     * Sauvegarde toutes les villes (Asynchrone via SQLite)
     */
    public void saveTowns(Map<String, Town> towns) {
        persistenceService.saveTowns(towns).thenRun(() -> {
            isDirty = false;
            lastSaveTime = System.currentTimeMillis();
        });
    }

    /**
     * Sauvegarde toutes les villes (Synchrone / Bloquant)
     * Utilisé lors du shutdown du serveur pour garantir que les données sont sauvegardées
     */
    public void saveTownsSync(Map<String, Town> towns) {
        try {
            plugin.getLogger().info("[TownDataManager] Sauvegarde synchrone de " + towns.size() + " villes...");
            persistenceService.saveTownsSync(towns); // Appel direct de la méthode synchrone
            isDirty = false;
            lastSaveTime = System.currentTimeMillis();
            plugin.getLogger().info("[TownDataManager] ✓ Sauvegarde synchrone terminée avec succès");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la sauvegarde synchrone des villes", e);
        }
    }

    /**
     * Marque les données comme modifiées (dirty)
     */
    public void markDirty() {
        isDirty = true;
    }

    /**
     * Sauvegarde uniquement si les données sont dirty ET que l'intervalle est dépassé
     */
    public void saveIfDirty(Map<String, Town> towns, boolean force) {
        if (!isDirty) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long timeSinceLastSave = currentTime - lastSaveTime;

        if (force || timeSinceLastSave >= SAVE_INTERVAL_MS) {
            plugin.getLogger().fine("[TownDataManager] Sauvegarde auto (dirty=" + isDirty + ")");
            saveTowns(towns);
        }
    }

    public void saveIfDirty(Map<String, Town> towns) {
        saveIfDirty(towns, false);
    }

    public boolean isDirty() {
        return isDirty;
    }

    public long getTimeSinceLastSave() {
        return System.currentTimeMillis() - lastSaveTime;
    }

    /**
     * Charge les villes depuis SQLite
     */
    public Map<String, Town> loadTowns() {
        return persistenceService.loadTowns();
    }

    /**
     * Supprime une ville de la base de données
     * @param townName Le nom de la ville à supprimer
     */
    public void deleteTownFromDB(String townName) {
        persistenceService.deleteTownFromDB(townName);
    }

    // ====================================================================================
    // LEGACY YAML SUPPORT (Pour la migration)
    // ====================================================================================

    private void loadConfig() {
        if (!townsFile.exists()) {
            return;
        }
        townsConfig = YamlConfiguration.loadConfiguration(townsFile);
    }

    public Map<String, Town> loadFromLegacyYAML() {
        Map<String, Town> towns = new HashMap<>();
        loadConfig();

        if (townsConfig == null) return towns;

        ConfigurationSection townsSection = townsConfig.getConfigurationSection("towns");
        if (townsSection == null) {
            return towns;
        }

        for (String townName : townsSection.getKeys(false)) {
            try {
                Town town = loadTownLegacy(townName, townsSection.getConfigurationSection(townName));
                if (town != null) {
                    towns.put(townName, town);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Erreur lors du chargement legacy de la ville " + townName, e);
            }
        }

        return towns;
    }

    private Town loadTownLegacy(String townName, ConfigurationSection section) {
        if (section == null) return null;

        UUID mayorUuid = UUID.fromString(section.getString("mayor-uuid"));
        String description = section.getString("description", "");

        // Création basique
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

        town.setDescription(description);
        double bankBalance = section.getDouble("bank-balance", 0.0);
        if (bankBalance > 0) town.deposit(bankBalance);
        town.setCitizenTax(section.getDouble("citizen-tax", 0.0));
        town.setCompanyTax(section.getDouble("company-tax", 0.0));

        if (section.contains("last-tax-collection")) {
            town.setLastTaxCollection(LocalDateTime.parse(section.getString("last-tax-collection"), DATE_FORMAT));
        }

        if (section.contains("level")) {
            String levelName = section.getString("level");
            TownLevel level = TownLevel.fromName(levelName);
            if (level != null) town.setLevel(level);
            else town.setLevel(TownLevel.CAMPEMENT);
        } else {
            town.setLevel(TownLevel.CAMPEMENT);
        }

        if (section.contains("spawn")) {
            try {
                String worldName = section.getString("spawn.world");
                org.bukkit.World world = plugin.getServer().getWorld(worldName);
                if (world != null) {
                    double x = section.getDouble("spawn.x");
                    double y = section.getDouble("spawn.y");
                    double z = section.getDouble("spawn.z");
                    float yaw = (float) section.getDouble("spawn.yaw", 0.0);
                    float pitch = (float) section.getDouble("spawn.pitch", 0.0);
                    town.setSpawnLocation(new org.bukkit.Location(world, x, y, z, yaw, pitch));
                }
            } catch (Exception e) {}
        }

        if (membersSection != null) {
            for (String key : membersSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(membersSection.getString(key + ".uuid"));
                    if (uuid.equals(mayorUuid)) continue;

                    String name = membersSection.getString(key + ".name");
                    List<String> roleNames = membersSection.getStringList(key + ".roles");
                    
                    if (roleNames != null && !roleNames.isEmpty()) {
                        Set<TownRole> roles = new HashSet<>();
                        for (String roleName : roleNames) {
                            try { roles.add(TownRole.valueOf(roleName)); } catch (Exception e) {}
                        }
                        if (!roles.isEmpty()) {
                            TownRole primaryRole = roles.stream().max((r1, r2) -> Integer.compare(r1.getPower(), r2.getPower())).orElse(TownRole.CITOYEN);
                            town.addMember(uuid, name, primaryRole);
                            TownMember member = town.getMember(uuid);
                            if (member != null) member.setRoles(roles);
                        } else {
                            town.addMember(uuid, name, TownRole.CITOYEN);
                        }
                    } else {
                        TownRole role = TownRole.valueOf(membersSection.getString(key + ".role", "CITOYEN"));
                        town.addMember(uuid, name, role);
                    }
                } catch (Exception e) {}
            }
        }

        ConfigurationSection invitationsSection = section.getConfigurationSection("invitations");
        if (invitationsSection != null) {
            for (String key : invitationsSection.getKeys(false)) {
                try {
                    UUID playerUuid = UUID.fromString(invitationsSection.getString(key + ".player-uuid"));
                    LocalDateTime inviteDate = LocalDateTime.parse(invitationsSection.getString(key + ".invite-date"), DATE_FORMAT);
                    town.invitePlayer(playerUuid, inviteDate);
                } catch (Exception e) {}
            }
        }

        ConfigurationSection plotsSection = section.getConfigurationSection("plots");
        if (plotsSection != null) {
            for (String key : plotsSection.getKeys(false)) {
                Plot plot = loadPlotLegacy(townName, plotsSection.getConfigurationSection(key));
                if (plot != null) town.addPlot(plot);
            }
        }

        return town;
    }

    private Plot loadPlotLegacy(String townName, ConfigurationSection section) {
        if (section == null) return null;

        String worldName = section.getString("world");
        List<String> chunkKeys = section.getStringList("chunks");
        boolean grouped = section.getBoolean("grouped", false);
        String groupName = section.getString("group-name");

        LocalDateTime claimDate = null;
        if (section.contains("claim-date")) {
            try { claimDate = LocalDateTime.parse(section.getString("claim-date"), DATE_FORMAT); } catch (Exception e) {}
        }

        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        Plot plot = null;

        if (world != null) {
            if (!chunkKeys.isEmpty()) {
                if (grouped) {
                    plot = new Plot(townName, worldName, chunkKeys, groupName);
                } else {
                    String firstChunk = chunkKeys.get(0);
                    String[] parts = firstChunk.split(":");
                    if (parts.length >= 3) {
                        int chunkX = Integer.parseInt(parts[1]);
                        int chunkZ = Integer.parseInt(parts[2]);
                        plot = new Plot(townName, world.getChunkAt(chunkX, chunkZ));
                    }
                }
            } else if (section.contains("chunk-x") && section.contains("chunk-z")) {
                int chunkX = section.getInt("chunk-x");
                int chunkZ = section.getInt("chunk-z");
                plot = new Plot(townName, world.getChunkAt(chunkX, chunkZ));
            }

            if (plot != null && claimDate != null) {
                plot.setClaimDate(claimDate);
            }
        }

        if (plot == null) return null;

        plot.setType(PlotType.valueOf(section.getString("type", "PUBLIC")));
        plot.setMunicipalSubType(MunicipalSubType.valueOf(section.getString("municipal-subtype", "NONE")));
        if (section.contains("plot-number")) plot.setPlotNumber(section.getString("plot-number"));

        if (section.contains("owner-uuid")) {
            plot.setOwner(UUID.fromString(section.getString("owner-uuid")), section.getString("owner-name"));
        }

        if (section.contains("company")) plot.setCompany(section.getString("company"));
        if (section.contains("company-siret")) plot.setCompanySiret(section.getString("company-siret"));

        if (section.contains("debt-amount")) {
            plot.setCompanyDebtAmount(section.getDouble("debt-amount"));
            plot.setDebtWarningCount(section.getInt("debt-warning-count", 0));
            if (section.contains("last-debt-warning")) {
                try { plot.setLastDebtWarningDate(LocalDateTime.parse(section.getString("last-debt-warning"), DATE_FORMAT)); } catch (Exception e) {}
            }
        }

        if (section.contains("particular-debt-amount")) {
            plot.setParticularDebtAmount(section.getDouble("particular-debt-amount"));
            plot.setParticularDebtWarningCount(section.getInt("particular-debt-warning-count", 0));
            if (section.contains("particular-last-debt-warning")) {
                try { plot.setParticularLastDebtWarningDate(LocalDateTime.parse(section.getString("particular-last-debt-warning"), DATE_FORMAT)); } catch (Exception e) {}
            }
        }

        if (section.getBoolean("for-sale", false)) {
            plot.setSalePrice(section.getDouble("sale-price"));
            plot.setForSale(true);
        }

        if (section.getBoolean("for-rent", false)) {
            if (section.contains("rent-price-per-day")) {
                plot.setRentPricePerDay(section.getDouble("rent-price-per-day"));
            } else if (section.contains("rent-price")) {
                double totalPrice = section.getDouble("rent-price");
                int duration = section.getInt("rent-duration", 1);
                plot.setRentPricePerDay(totalPrice / Math.max(1, duration));
            }
            plot.setForRent(true);
        }

        if (section.contains("renter-uuid")) {
            UUID renterUuid = UUID.fromString(section.getString("renter-uuid"));
            if (section.contains("rent-end-date")) {
                try {
                    LocalDateTime rentEndDate = LocalDateTime.parse(section.getString("rent-end-date"), DATE_FORMAT);
                    plot.setRenter(renterUuid, 1);
                    plot.setRentEndDate(rentEndDate);
                } catch (Exception e) {
                    int days = section.getInt("rent-days-remaining", 1);
                    plot.setRenter(renterUuid, days);
                }
            } else {
                int days = section.getInt("rent-days-remaining", 1);
                plot.setRenter(renterUuid, days);
            }
        }

        if (section.contains("renter-company-siret")) plot.setRenterCompanySiret(section.getString("renter-company-siret"));

        if (world != null) {
            List<String> protectedBlocksList;
            if (section.isString("protected-blocks")) {
                String compact = section.getString("protected-blocks", "");
                protectedBlocksList = compact.isEmpty() ? new ArrayList<>() : Arrays.asList(compact.split(", "));
            } else {
                protectedBlocksList = section.getStringList("protected-blocks");
            }
            for (String key : protectedBlocksList) {
                try {
                    String[] parts = key.split(":");
                    if (parts.length == 3) {
                        plot.addProtectedBlock(new Location(world, Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
                    }
                } catch (Exception e) {}
            }
        }

        ConfigurationSection permsSection = section.getConfigurationSection("permissions");
        if (permsSection != null) {
            for (String key : permsSection.getKeys(false)) {
                ConfigurationSection permSection = permsSection.getConfigurationSection(key);
                if (permSection != null) {
                    UUID playerUuid = UUID.fromString(permSection.getString("player"));
                    List<String> permsList = permSection.getStringList("perms");
                    Set<PlotPermission> permissions = EnumSet.noneOf(PlotPermission.class);
                    for (String permName : permsList) {
                        try { permissions.add(PlotPermission.valueOf(permName)); } catch (Exception e) {}
                    }
                    plot.setPlayerPermissions(playerUuid, permissions);
                }
            }
        }

        for (String uuidStr : section.getStringList("trusted-players")) {
            try { plot.addTrustedPlayer(UUID.fromString(uuidStr)); } catch (Exception e) {}
        }

        ConfigurationSection flagsSection = section.getConfigurationSection("flags");
        if (flagsSection != null) {
            for (String flagName : flagsSection.getKeys(false)) {
                try {
                    PlotFlag flag = PlotFlag.valueOf(flagName);
                    plot.setFlag(flag, flagsSection.getBoolean(flagName));
                } catch (Exception e) {}
            }
        }

        if (section.contains("prison-spawn")) {
            try {
                String pwName = section.getString("prison-spawn.world");
                World pw = plugin.getServer().getWorld(pwName);
                if (pw != null) {
                    double x = section.getDouble("prison-spawn.x");
                    double y = section.getDouble("prison-spawn.y");
                    double z = section.getDouble("prison-spawn.z");
                    float yaw = (float) section.getDouble("prison-spawn.yaw", 0.0);
                    float pitch = (float) section.getDouble("prison-spawn.pitch", 0.0);
                    plot.setPrisonSpawn(new Location(pw, x, y, z, yaw, pitch));
                }
            } catch (Exception e) {}
        }

        return plot;
    }
}