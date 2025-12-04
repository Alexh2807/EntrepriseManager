package com.gravityyfh.roleplaycity.mdt.config;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mdt.data.MDTBed;
import com.gravityyfh.roleplaycity.mdt.data.MDTGenerator;
import com.gravityyfh.roleplaycity.mdt.data.MDTTeam;
import com.gravityyfh.roleplaycity.mdt.data.ResourceSpawner;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MDTConfig {
    private final RoleplayCity plugin;
    private File configFile;
    private FileConfiguration config;
    private boolean enabled;
    private String worldName;
    private int lobbyCountdown;
    private int gameStartCountdown;
    private int maxGameDuration;
    private boolean tntFlyEnabled;
    private double tntDamageSelf;
    private double tntDamageTeammates;
    private Location lobbySpawn;
    private Location redTeamSpawn;
    private Location blueTeamSpawn;
    private List<ResourceSpawner> teamGenResources;
    private List<ResourceSpawner> midGenResources;
    private String prefix;
    private Set<Material> unbreakableBlocks;
    // Nouveaux champs pour le flux de jeu ameliore
    private int joinPeriod;
    private int voidKillHeight;

    public MDTConfig(RoleplayCity plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File mdtFolder = new File(plugin.getDataFolder(), "mdt");
        if (!mdtFolder.exists()) mdtFolder.mkdirs();
        configFile = new File(mdtFolder, "mdt.yml");
        if (!configFile.exists()) plugin.saveResource("mdt/mdt.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);
        InputStream defaultStream = plugin.getResource("mdt/mdt.yml");
        if (defaultStream != null) {
            config.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
        }
        cacheValues();
    }

    private void cacheValues() {
        enabled = config.getBoolean("enabled", true);
        worldName = config.getString("world-name", "mdt");
        joinPeriod = config.getInt("timers.join-period", 60);
        lobbyCountdown = config.getInt("timers.lobby-countdown", 60);
        gameStartCountdown = config.getInt("timers.pre-game", 5);
        maxGameDuration = config.getInt("game.max-duration", 3600);
        voidKillHeight = config.getInt("game.void-kill-height", -35);
        tntFlyEnabled = config.getBoolean("game.tnt-fly-enabled", true);
        tntDamageSelf = config.getDouble("game.tnt-damage-self", 2.0);
        tntDamageTeammates = config.getDouble("game.tnt-damage-teammates", 0.0);
        World world = getWorld();
        if (world != null) {
            lobbySpawn = loadLocation("locations.spawns.lobby", world);
            redTeamSpawn = loadLocation("locations.spawns.red", world);
            blueTeamSpawn = loadLocation("locations.spawns.blue", world);
        }
        loadResourceRules();
        loadUnbreakableBlocks();
        prefix = config.getString("messages.prefix", "&8[&cMDT&8] &r");
    }

    private void loadUnbreakableBlocks() {
        unbreakableBlocks = new HashSet<>();
        // Blocs par défaut toujours protégés
        unbreakableBlocks.add(Material.BEDROCK);
        unbreakableBlocks.add(Material.BARRIER);

        // Charger depuis la config
        List<String> blockList = config.getStringList("game.unbreakable-blocks");
        for (String blockName : blockList) {
            Material mat = Material.matchMaterial(blockName.toUpperCase());
            if (mat != null) {
                unbreakableBlocks.add(mat);
            } else {
                plugin.getLogger().warning("[MDT] Bloc inconnu dans unbreakable-blocks: " + blockName);
            }
        }
    }

    private void loadResourceRules() {
        teamGenResources = new ArrayList<>();
        midGenResources = new ArrayList<>();
        ConfigurationSection genSection = config.getConfigurationSection("generators");
        if (genSection == null) return;
        for (String key : genSection.getKeys(false)) {
            ConfigurationSection section = genSection.getConfigurationSection(key);
            if (section == null) continue;
            String matName = section.getString("material", "BRICK");
            Material material = Material.matchMaterial(matName);
            if (material == null) material = Material.BRICK;
            String name = section.getString("name", matName);
            int interval = section.getInt("interval", 1);
            int maxStack = section.getInt("max-stack", 64);
            boolean spawnAtBase = section.getBoolean("spawn-at-base", true);
            ResourceSpawner spawner = new ResourceSpawner(material, name, interval, maxStack);
            if (spawnAtBase) {
                teamGenResources.add(spawner);
            } else {
                midGenResources.add(spawner);
            }
        }
    }

    private Location loadLocation(String path, World world) {
        if (!config.contains(path)) return null;
        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");
        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void saveLocationToConfig(String path, Location loc) {
        config.set(path + ".x", loc.getX());
        config.set(path + ".y", loc.getY());
        config.set(path + ".z", loc.getZ());
        config.set(path + ".yaw", loc.getYaw());
        config.set(path + ".pitch", loc.getPitch());
        saveConfig();
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save mdt.yml!");
        }
    }

    public void setLobbySpawn(Location loc) {
        this.lobbySpawn = loc;
        saveLocationToConfig("locations.spawns.lobby", loc);
    }

    public void setTeamSpawn(MDTTeam team, Location loc) {
        if (team == MDTTeam.RED) {
            this.redTeamSpawn = loc;
            saveLocationToConfig("locations.spawns.red", loc);
        } else {
            this.blueTeamSpawn = loc;
            saveLocationToConfig("locations.spawns.blue", loc);
        }
    }

    public void addGeneratorLocation(Location loc, String type) {
        List<String> serializedLocs = config.getStringList("locations.generators");
        String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "," + type.toLowerCase();
        if (!serializedLocs.contains(entry)) {
            serializedLocs.add(entry);
            config.set("locations.generators", serializedLocs);
            saveConfig();
        }
    }

    public void clearGenerators() {
        config.set("locations.generators", new ArrayList<>());
        saveConfig();
    }

    public void addMerchantLocation(Location loc, MerchantType type) {
        List<String> serializedLocs = config.getStringList("locations.merchants");
        // Format: x,y,z,yaw,TYPE (yaw pour la direction du villageois)
        float yaw = loc.getYaw();
        // Arrondir le yaw aux 4 directions cardinales (N/S/E/O)
        yaw = Math.round(yaw / 90f) * 90f;
        String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + "," + (int)yaw + "," + type.name();
        // Vérifier si un marchand existe déjà à cette position (ignorer le yaw pour la comparaison)
        String coordPrefix = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ",";
        boolean exists = serializedLocs.stream().anyMatch(s -> s.startsWith(coordPrefix));
        if (!exists) {
            serializedLocs.add(entry);
            config.set("locations.merchants", serializedLocs);
            saveConfig();
        }
    }

    public void clearMerchants() {
        config.set("locations.merchants", new ArrayList<>());
        saveConfig();
    }

    public List<MDTGenerator> loadGeneratorsFromLocations() {
        List<MDTGenerator> activeGenerators = new ArrayList<>();
        World world = getWorld();
        if (world == null) return activeGenerators;
        loadResourceRules();
        List<String> serializedLocs = config.getStringList("locations.generators");
        for (String entry : serializedLocs) {
            try {
                String[] parts = entry.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                String type = parts[3].toUpperCase();
                Location loc = new Location(world, x + 0.5, y, z + 0.5);
                List<ResourceSpawner> resources = new ArrayList<>();
                if (type.equals("UNIVERSAL")) {
                    resources.addAll(teamGenResources);
                    resources.addAll(midGenResources);
                } else {
                    ResourceSpawner spawner = findSpawnerByType(type);
                    if (spawner != null) {
                        resources.add(spawner);
                    }
                }
                if (!resources.isEmpty()) {
                    activeGenerators.add(new MDTGenerator(loc, resources, type));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid generator location: " + entry);
            }
        }
        return activeGenerators;
    }

    private ResourceSpawner findSpawnerByType(String type) {
        List<ResourceSpawner> all = new ArrayList<>(teamGenResources);
        all.addAll(midGenResources);
        for (ResourceSpawner s : all) {
            if (s.getMaterial().name().equalsIgnoreCase(type) || s.getDisplayName().toLowerCase().contains(type.toLowerCase())) return s;
        }
        return null;
    }

    public List<MDTBed> loadTeamBeds() {
        List<MDTBed> beds = new ArrayList<>();
        World world = getWorld();
        if (world == null) return beds;
        Location redLoc = loadLocation("locations.beds.red", world);
        if (redLoc != null) beds.add(new MDTBed("bed_red", redLoc, MDTTeam.RED));
        Location blueLoc = loadLocation("locations.beds.blue", world);
        if (blueLoc != null) beds.add(new MDTBed("bed_blue", blueLoc, MDTTeam.BLUE));
        return beds;
    }

    public List<MDTBed> loadNeutralBeds() {
        List<MDTBed> beds = new ArrayList<>();
        World world = getWorld();
        if (world == null) return beds;
        List<String> serializedLocs = config.getStringList("locations.beds.neutral");
        int i = 1;
        for (String entry : serializedLocs) {
            try {
                String[] parts = entry.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                Location loc = new Location(world, x, y, z);
                beds.add(new MDTBed("neutral_" + i++, loc, 2));
            } catch (Exception e) {}
        }
        return beds;
    }

    public void setBedLocation(MDTTeam team, Location loc) {
        saveLocationToConfig("locations.beds." + team.name().toLowerCase(), loc);
    }

    public void addNeutralBedLocation(Location loc) {
        List<String> serializedLocs = config.getStringList("locations.beds.neutral");
        String entry = loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
        if (!serializedLocs.contains(entry)) {
            serializedLocs.add(entry);
            config.set("locations.beds.neutral", serializedLocs);
            saveConfig();
        }
    }

    public int getMaxBonusHearts() {
        return config.getInt("game.max-bonus-hearts", 4);
    }

    public Location getBedLocation(MDTTeam team) {
        World world = getWorld();
        if (world == null) return null;
        return loadLocation("locations.beds." + team.name().toLowerCase(), world);
    }

    public enum MerchantType { BLOCKS, WEAPONS, ARMOR, SPECIAL, GLOBAL }

    public static class TradeConfig {
        public final Material resultMaterial;
        public final int resultAmount;
        public final Material costMaterial;
        public final int costAmount;
        public final List<String> enchantments;
        public final String displayName;
        // Support items custom (custom:dynamite)
        public final boolean isCustomItem;
        public final String customItemId;

        public TradeConfig(Material rm, int ra, Material cm, int ca, List<String> ench, String dn) {
            this.resultMaterial = rm; this.resultAmount = ra; this.costMaterial = cm; this.costAmount = ca;
            this.enchantments = ench; this.displayName = dn;
            this.isCustomItem = false; this.customItemId = null;
        }

        // Constructeur pour items custom
        public TradeConfig(String customId, int ra, Material cm, int ca, String dn) {
            this.resultMaterial = null; this.resultAmount = ra; this.costMaterial = cm; this.costAmount = ca;
            this.enchantments = null; this.displayName = dn;
            this.isCustomItem = true; this.customItemId = customId;
        }
    }

    public List<TradeConfig> loadTrades(MerchantType type) {
        List<TradeConfig> trades = new ArrayList<>();
        if (type == MerchantType.GLOBAL) {
            trades.addAll(loadTrades(MerchantType.BLOCKS));
            trades.addAll(loadTrades(MerchantType.WEAPONS));
            trades.addAll(loadTrades(MerchantType.ARMOR));
            trades.addAll(loadTrades(MerchantType.SPECIAL));
            return trades;
        }
        String path = "shops." + type.name().toLowerCase() + ".items";
        List<Map<?, ?>> tradeList = config.getMapList(path);
        for (Map<?, ?> map : tradeList) {
            try {
                String matName = (String) map.get("item");
                Object amountObj = map.get("amount");
                int amount = amountObj instanceof Number ? ((Number) amountObj).intValue() : 1;
                String costMatName = (String) map.get("price-material");
                Material costMat = Material.matchMaterial(costMatName);
                Object costAmountObj = map.get("price-amount");
                int costAmount = costAmountObj instanceof Number ? ((Number) costAmountObj).intValue() : 1;
                String name = (String) map.get("name");

                if (costMat == null) continue;

                // Support items custom (format: custom:<id>)
                if (matName != null && matName.startsWith("custom:")) {
                    String customId = matName.substring(7); // Enleve "custom:"
                    trades.add(new TradeConfig(customId, amount, costMat, costAmount, name));
                } else {
                    Material mat = Material.matchMaterial(matName);
                    if (mat != null) {
                        @SuppressWarnings("unchecked")
                        List<String> enchants = (List<String>) map.get("enchantments");
                        trades.add(new TradeConfig(mat, amount, costMat, costAmount, enchants, name));
                    }
                }
            } catch (Exception e) {}
        }
        return trades;
    }

    public String getShopTitle(MerchantType type) {
        if (type == MerchantType.GLOBAL) return "Marchand Fourre-Tout";
        return config.getString("shops." + type.name().toLowerCase() + ".title", "Marchand");
    }

    public static class MerchantData {
        public final Location location;
        public final MerchantType type;
        public MerchantData(Location l, MerchantType t) { this.location = l; this.type = t; }
    }

    public List<MerchantData> loadMerchants() {
        List<MerchantData> list = new ArrayList<>();
        World world = getWorld();
        if (world == null) return list;
        for (String entry : config.getStringList("locations.merchants")) {
            try {
                String[] parts = entry.split(",");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);

                // Support ancien format (x,y,z,TYPE) et nouveau format (x,y,z,yaw,TYPE)
                float yaw = 0;
                MerchantType type;
                if (parts.length == 5) {
                    // Nouveau format avec yaw
                    yaw = Float.parseFloat(parts[3]);
                    type = MerchantType.valueOf(parts[4]);
                } else {
                    // Ancien format sans yaw
                    type = MerchantType.valueOf(parts[3]);
                }

                Location loc = new Location(world, x + 0.5, y, z + 0.5, yaw, 0);
                list.add(new MerchantData(loc, type));
            } catch (Exception e) {}
        }
        return list;
    }

    public boolean isEnabled() { return enabled; }
    public String getWorldName() { return worldName; }
    public World getWorld() { return Bukkit.getWorld(worldName); }
    public Location getLobbySpawn() { return lobbySpawn; }
    public Location getTeamSpawn(MDTTeam team) { return team == MDTTeam.RED ? redTeamSpawn : blueTeamSpawn; }
    public int getLobbyCountdown() { return lobbyCountdown; }
    public int getGameStartCountdown() { return gameStartCountdown; }
    public int getMaxGameDuration() { return maxGameDuration; }
    public boolean isTntFlyEnabled() { return tntFlyEnabled; }
    public double getTntDamageSelf() { return tntDamageSelf; }
    public double getTntDamageTeammates() { return tntDamageTeammates; }
    public String getFormattedMessage(String key, Object... replacements) {
        String msg = config.getString("messages." + key, "");
        for (int i = 0; i < replacements.length; i += 2) {
            msg = msg.replace(String.valueOf(replacements[i]), String.valueOf(replacements[i+1]));
        }
        return (prefix + msg).replace("&", "§");
    }
    public Sound getSound(String key) {
        try { return Sound.valueOf(config.getString("sounds." + key, "ENTITY_EXPERIENCE_ORB_PICKUP")); } catch (Exception e) { return Sound.ENTITY_EXPERIENCE_ORB_PICKUP; }
    }

    // Méthodes pour les équipes
    public int getMinPlayersPerTeam() { return config.getInt("teams.min-players", 1); }
    public int getMaxPlayersPerTeam() { return config.getInt("teams.max-players", 4); }
    public int getMinTotalPlayers() { return getMinPlayersPerTeam() * 2; }
    public int getMaxTotalPlayers() { return getMaxPlayersPerTeam() * 2; }

    public Location getRedTeamSpawn() { return redTeamSpawn; }
    public Location getBlueTeamSpawn() { return blueTeamSpawn; }

    /**
     * Retourne l'objet FileConfiguration pour un accès direct
     */
    public FileConfiguration getFileConfig() { return config; }

    /**
     * Vérifie si un bloc est dans la blacklist des blocs incassables
     */
    public boolean isUnbreakableBlock(Material material) {
        return unbreakableBlocks != null && unbreakableBlocks.contains(material);
    }

    /**
     * Retourne la liste des blocs incassables
     */
    public Set<Material> getUnbreakableBlocks() {
        return unbreakableBlocks != null ? unbreakableBlocks : new HashSet<>();
    }

    // Nouveaux getters pour le flux de jeu ameliore
    public int getJoinPeriod() { return joinPeriod; }
    public int getVoidKillHeight() { return voidKillHeight; }

    /**
     * Retourne la reference au plugin principal (pour CustomItemManager)
     */
    public RoleplayCity getPlugin() { return plugin; }
}
