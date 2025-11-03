package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class TownDataManager {
    private final RoleplayCity plugin;
    private final File townsFile;
    private FileConfiguration townsConfig;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

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

            // Parcelles
            int plotIndex = 0;
            for (Plot plot : town.getPlots().values()) {
                String plotPath = path + ".plots." + plotIndex;
                townsConfig.set(plotPath + ".world", plot.getWorldName());
                townsConfig.set(plotPath + ".chunk-x", plot.getChunkX());
                townsConfig.set(plotPath + ".chunk-z", plot.getChunkZ());
                townsConfig.set(plotPath + ".type", plot.getType().name());
                townsConfig.set(plotPath + ".municipal-subtype", plot.getMunicipalSubType().name());
                townsConfig.set(plotPath + ".claim-date", plot.getClaimDate().format(DATE_FORMAT));

                if (plot.getOwnerUuid() != null) {
                    townsConfig.set(plotPath + ".owner-uuid", plot.getOwnerUuid().toString());
                    townsConfig.set(plotPath + ".owner-name", plot.getOwnerName());
                }

                if (plot.getCompanyName() != null) {
                    townsConfig.set(plotPath + ".company", plot.getCompanyName());
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
                    townsConfig.set(plotPath + ".last-rent-update", plot.getLastRentUpdate().format(DATE_FORMAT));
                    townsConfig.set(plotPath + ".rent-days-remaining", plot.getRentDaysRemaining());
                }

                // Sauvegarder les blocs protégés
                if (!plot.getProtectedBlocks().isEmpty()) {
                    townsConfig.set(plotPath + ".protected-blocks", new ArrayList<>(plot.getProtectedBlocks()));
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

                plotIndex++;
            }

            // Groupes de parcelles
            int groupIndex = 0;
            for (PlotGroup group : town.getPlotGroups().values()) {
                String groupPath = path + ".groups." + groupIndex;
                townsConfig.set(groupPath + ".id", group.getGroupId());
                townsConfig.set(groupPath + ".name", group.getGroupName());

                if (group.getOwnerUuid() != null) {
                    townsConfig.set(groupPath + ".owner-uuid", group.getOwnerUuid().toString());
                    townsConfig.set(groupPath + ".owner-name", group.getOwnerName());
                }

                townsConfig.set(groupPath + ".creation-date", group.getCreationDate().format(DATE_FORMAT));
                townsConfig.set(groupPath + ".plots", new ArrayList<>(group.getPlotKeys()));

                if (group.isForRent()) {
                    townsConfig.set(groupPath + ".for-rent", true);
                    townsConfig.set(groupPath + ".rent-price-per-day", group.getRentPricePerDay());
                }

                if (group.isForSale()) {
                    townsConfig.set(groupPath + ".for-sale", true);
                    townsConfig.set(groupPath + ".sale-price", group.getSalePrice());
                }

                if (group.getRenterUuid() != null) {
                    townsConfig.set(groupPath + ".renter-uuid", group.getRenterUuid().toString());
                    townsConfig.set(groupPath + ".rent-start", group.getRentStartDate().format(DATE_FORMAT));
                    townsConfig.set(groupPath + ".last-rent-update", group.getLastRentUpdate().format(DATE_FORMAT));
                    townsConfig.set(groupPath + ".rent-days-remaining", group.getRentDaysRemaining());
                }

                groupIndex++;
            }
        }

        try {
            townsConfig.save(townsFile);
            plugin.getLogger().info("Sauvegardé " + towns.size() + " villes dans towns.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Erreur lors de la sauvegarde des villes: " + e.getMessage());
        }
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
                Town town = loadTown(townName, townsSection.getConfigurationSection(townName));
                if (town != null) {
                    towns.put(townName, town);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Erreur lors du chargement de la ville " + townName + ": " + e.getMessage());
                e.printStackTrace();
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

        // Charger les membres (sauf le maire déjà ajouté)
        if (membersSection != null) {
            for (String key : membersSection.getKeys(false)) {
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
            }
        }

        // Charger les parcelles
        ConfigurationSection plotsSection = section.getConfigurationSection("plots");
        if (plotsSection != null) {
            for (String key : plotsSection.getKeys(false)) {
                Plot plot = loadPlot(townName, plotsSection.getConfigurationSection(key));
                if (plot != null) {
                    town.addPlot(plot);
                }
            }
        }

        // Charger les groupes de parcelles
        ConfigurationSection groupsSection = section.getConfigurationSection("groups");
        if (groupsSection != null) {
            for (String key : groupsSection.getKeys(false)) {
                PlotGroup group = loadPlotGroup(townName, groupsSection.getConfigurationSection(key), town);
                if (group != null && group.isValid()) {
                    town.addPlotGroup(group);
                }
            }
        }

        return town;
    }

    private Plot loadPlot(String townName, ConfigurationSection section) {
        if (section == null) return null;

        String worldName = section.getString("world");
        int chunkX = section.getInt("chunk-x");
        int chunkZ = section.getInt("chunk-z");

        // Créer un pseudo-chunk pour le constructeur
        org.bukkit.World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("Monde introuvable pour la parcelle: " + worldName);
            return null;
        }

        Plot plot = new Plot(townName, world.getChunkAt(chunkX, chunkZ));

        // Charger le type et sous-type
        plot.setType(PlotType.valueOf(section.getString("type", "MUNICIPAL")));
        plot.setMunicipalSubType(MunicipalSubType.valueOf(
            section.getString("municipal-subtype", "NONE")));

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
            int daysRemaining = section.getInt("rent-days-remaining", 1);
            plot.setRenter(renterUuid, daysRemaining);

            // Charger les dates si disponibles
            if (section.contains("last-rent-update")) {
                // Les dates sont déjà définies par setRenter, mais on peut les surcharger
                try {
                    LocalDateTime lastUpdate = LocalDateTime.parse(
                        section.getString("last-rent-update"), DATE_FORMAT);
                    // Note: il faudrait un setter pour lastRentUpdate si besoin de précision
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement de last-rent-update");
                }
            }
        }

        // Charger les blocs protégés
        List<String> protectedBlocksList = section.getStringList("protected-blocks");
        if (!protectedBlocksList.isEmpty()) {
            for (String blockKey : protectedBlocksList) {
                try {
                    String[] parts = blockKey.split(":");
                    if (parts.length == 3) {
                        Location loc = new Location(
                            plugin.getServer().getWorld(worldName),
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

        return plot;
    }

    private PlotGroup loadPlotGroup(String townName, ConfigurationSection section, Town town) {
        if (section == null) return null;

        String groupId = section.getString("id");
        PlotGroup group = new PlotGroup(groupId, townName);

        // Charger les infos de base
        group.setGroupName(section.getString("name", "Groupe sans nom"));

        if (section.contains("owner-uuid")) {
            UUID ownerUuid = UUID.fromString(section.getString("owner-uuid"));
            String ownerName = section.getString("owner-name");
            group.setOwner(ownerUuid, ownerName);
        }

        // Charger les parcelles du groupe
        List<String> plotKeys = section.getStringList("plots");
        for (String plotKey : plotKeys) {
            String[] parts = plotKey.split(":");
            if (parts.length == 3) {
                String worldName = parts[0];
                int chunkX = Integer.parseInt(parts[1]);
                int chunkZ = Integer.parseInt(parts[2]);

                Plot plot = town.getPlot(worldName, chunkX, chunkZ);
                if (plot != null) {
                    group.addPlot(plot);
                }
            }
        }

        // Charger vente
        if (section.getBoolean("for-sale", false)) {
            group.setSalePrice(section.getDouble("sale-price"));
            group.setForSale(true);
        }

        // Charger location
        if (section.getBoolean("for-rent", false)) {
            group.setRentPricePerDay(section.getDouble("rent-price-per-day"));
            group.setForRent(true);
        }

        // Charger locataire
        if (section.contains("renter-uuid")) {
            UUID renterUuid = UUID.fromString(section.getString("renter-uuid"));
            int daysRemaining = section.getInt("rent-days-remaining", 1);
            group.setRenter(renterUuid, daysRemaining);

            if (section.contains("last-rent-update")) {
                try {
                    LocalDateTime lastUpdate = LocalDateTime.parse(
                        section.getString("last-rent-update"), DATE_FORMAT);
                    group.setLastRentUpdate(lastUpdate);
                } catch (Exception e) {
                    plugin.getLogger().warning("Erreur lors du chargement de last-rent-update du groupe");
                }
            }
        }

        return group;
    }
}
