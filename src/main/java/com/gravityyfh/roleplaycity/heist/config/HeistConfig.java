package com.gravityyfh.roleplaycity.heist.config;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Gestionnaire de configuration pour le système de cambriolage
 * Charge et fournit l'accès aux paramètres de cambriolage.yml
 */
public class HeistConfig {

    private final RoleplayCity plugin;
    private FileConfiguration config;
    private File configFile;

    // Cache des valeurs fréquemment utilisées
    private boolean enabled;
    private int minOnlineCitizens;
    private int minOnlinePolice;
    private boolean requireCommissariat;
    private List<PlotType> targetPlotTypes;

    private int countdownDuration;
    private int robberyDuration;
    private int disconnectGraceMinutes;

    private int plotCooldownHours;
    private int playerCooldownHours;

    private float explosionPower;
    private float explosionRadius;
    private boolean breakBlocks;
    private boolean damageEntities;
    private boolean createFire;
    private int particleCount;
    private double particleRadius;

    private int maxDefuseAttempts;
    private int wrongWirePenaltySeconds;
    private List<String> defuseHints;

    private double arrestFine;
    private boolean deathDropsLoot;
    private int prisonTimeMinutes;

    private boolean broadcastHeistStart;
    private boolean broadcastHeistEnd;
    private boolean notifyOwnerOnJoin;
    private Sound policeAlertSound;

    public HeistConfig(RoleplayCity plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    /**
     * Charge ou recharge la configuration
     */
    public void loadConfig() {
        // Créer le fichier s'il n'existe pas
        configFile = new File(plugin.getDataFolder(), "cambriolage.yml");
        if (!configFile.exists()) {
            plugin.saveResource("cambriolage.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Charger les valeurs par défaut depuis le JAR
        InputStream defaultStream = plugin.getResource("cambriolage.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaultConfig);
        }

        // Charger les valeurs en cache
        cacheValues();

        plugin.getLogger().info("[Heist] Configuration chargée depuis cambriolage.yml");
    }

    /**
     * Met en cache les valeurs de configuration
     */
    private void cacheValues() {
        // Général
        enabled = config.getBoolean("enabled", true);

        // Conditions
        minOnlineCitizens = config.getInt("conditions.min-online-citizens", 5);
        minOnlinePolice = config.getInt("conditions.min-online-police", 2);
        requireCommissariat = config.getBoolean("conditions.require-commissariat", true);

        targetPlotTypes = new ArrayList<>();
        for (String typeName : config.getStringList("conditions.target-plot-types")) {
            try {
                targetPlotTypes.add(PlotType.valueOf(typeName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[Heist] Type de terrain invalide dans config: " + typeName);
            }
        }
        if (targetPlotTypes.isEmpty()) {
            targetPlotTypes.add(PlotType.PARTICULIER);
            targetPlotTypes.add(PlotType.PROFESSIONNEL);
        }

        // Timers
        countdownDuration = config.getInt("timers.countdown-duration", 180);
        robberyDuration = config.getInt("timers.robbery-duration", 300);
        disconnectGraceMinutes = config.getInt("timers.disconnect-grace-minutes", 5);

        // Cooldowns
        plotCooldownHours = config.getInt("cooldowns.plot-cooldown-hours", 48);
        playerCooldownHours = config.getInt("cooldowns.player-cooldown-hours", 24);

        // Bombe
        explosionPower = (float) config.getDouble("bomb.explosion-power", 4.0);
        explosionRadius = (float) config.getDouble("bomb.explosion-radius", explosionPower);
        breakBlocks = config.getBoolean("bomb.break-blocks", true);
        damageEntities = config.getBoolean("bomb.damage-entities", true);
        createFire = config.getBoolean("bomb.create-fire", false);
        particleCount = config.getInt("bomb.particle-count", 50);
        particleRadius = config.getDouble("bomb.particle-radius", 1.2);

        // Puzzle désamorçage
        maxDefuseAttempts = config.getInt("defuse-puzzle.max-attempts", 3);
        wrongWirePenaltySeconds = config.getInt("defuse-puzzle.wrong-wire-penalty-seconds", 30);
        defuseHints = config.getStringList("defuse-puzzle.hints");
        if (defuseHints.isEmpty()) {
            defuseHints.add("Ne coupez pas le fil de la couleur du sang");
            defuseHints.add("Ne coupez pas le fil de la couleur du ciel");
            defuseHints.add("Ne coupez pas le fil de la couleur de l'herbe");
            defuseHints.add("Ne coupez pas le fil de la couleur du soleil");
            defuseHints.add("Ne coupez pas le fil sans couleur");
        }

        // Pénalités
        arrestFine = config.getDouble("penalties.arrest-fine", 5000);
        deathDropsLoot = config.getBoolean("penalties.death-drops-loot", true);
        prisonTimeMinutes = config.getInt("penalties.prison-time-minutes", 30);

        // Notifications
        broadcastHeistStart = config.getBoolean("notifications.broadcast-heist-start", true);
        broadcastHeistEnd = config.getBoolean("notifications.broadcast-heist-end", true);
        notifyOwnerOnJoin = config.getBoolean("notifications.notify-owner-on-join", true);

        String soundName = config.getString("notifications.police-alert-sound", "ENTITY_WITHER_SPAWN");
        try {
            policeAlertSound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            policeAlertSound = Sound.ENTITY_WITHER_SPAWN;
            plugin.getLogger().warning("[Heist] Son invalide: " + soundName + ", utilisation de ENTITY_WITHER_SPAWN");
        }
    }

    /**
     * Recharge la configuration depuis le fichier
     */
    public void reload() {
        loadConfig();
    }

    // === GETTERS - CONDITIONS ===

    public boolean isEnabled() {
        return enabled;
    }

    public int getMinOnlineCitizens() {
        return minOnlineCitizens;
    }

    public int getMinOnlinePolice() {
        return minOnlinePolice;
    }

    public boolean isCommissariatRequired() {
        return requireCommissariat;
    }

    public List<PlotType> getTargetPlotTypes() {
        return targetPlotTypes;
    }

    public boolean isPlotTypeTargetable(PlotType type) {
        return targetPlotTypes.contains(type);
    }

    // === GETTERS - TIMERS ===

    public int getCountdownDuration() {
        return countdownDuration;
    }

    public int getRobberyDuration() {
        return robberyDuration;
    }

    public int getDisconnectGraceMinutes() {
        return disconnectGraceMinutes;
    }

    // === GETTERS - COOLDOWNS ===

    public int getPlotCooldownHours() {
        return plotCooldownHours;
    }

    public int getPlayerCooldownHours() {
        return playerCooldownHours;
    }

    // === GETTERS - BOMBE ===

    public float getExplosionPower() {
        return explosionPower;
    }

    public float getExplosionRadius() {
        return explosionRadius;
    }

    public boolean shouldBreakBlocks() {
        return breakBlocks;
    }

    public boolean shouldDamageEntities() {
        return damageEntities;
    }

    public boolean shouldCreateFire() {
        return createFire;
    }

    public int getParticleCount() {
        return particleCount;
    }

    public double getParticleRadius() {
        return particleRadius;
    }

    // === GETTERS - PUZZLE ===

    public int getMaxDefuseAttempts() {
        return maxDefuseAttempts;
    }

    public int getWrongWirePenaltySeconds() {
        return wrongWirePenaltySeconds;
    }

    public List<String> getDefuseHints() {
        return defuseHints;
    }

    /**
     * Récupère l'indice pour un fil spécifique
     * @param wireIndex index du fil (0-4)
     * @return l'indice correspondant
     */
    public String getHintForWire(int wireIndex) {
        if (wireIndex >= 0 && wireIndex < defuseHints.size()) {
            return defuseHints.get(wireIndex);
        }
        return "Choisissez le bon fil...";
    }

    // === GETTERS - PÉNALITÉS ===

    public double getArrestFine() {
        return arrestFine;
    }

    public boolean shouldDeathDropLoot() {
        return deathDropsLoot;
    }

    public int getPrisonTimeMinutes() {
        return prisonTimeMinutes;
    }

    // === GETTERS - NOTIFICATIONS ===

    public boolean shouldBroadcastHeistStart() {
        return broadcastHeistStart;
    }

    public boolean shouldBroadcastHeistEnd() {
        return broadcastHeistEnd;
    }

    public boolean shouldNotifyOwnerOnJoin() {
        return notifyOwnerOnJoin;
    }

    public Sound getPoliceAlertSound() {
        return policeAlertSound;
    }

    // === GETTERS - MESSAGES ===

    public String getMessage(String key) {
        String prefix = config.getString("messages.prefix", "&8[&4Cambriolage&8] ");
        String message = config.getString("messages." + key, "&cMessage non trouvé: " + key);
        return prefix + message;
    }

    public String getMessageRaw(String key) {
        return config.getString("messages." + key, "&cMessage non trouvé: " + key);
    }

    // === GETTERS - EFFETS ===

    public boolean isTimerHologramEnabled() {
        return config.getBoolean("effects.show-timer-hologram", true);
    }

    public boolean areBombParticlesEnabled() {
        return config.getBoolean("effects.bomb-particles.enabled", true);
    }

    public String getBombParticleType() {
        return config.getString("effects.bomb-particles.type", "FLAME");
    }

    public int getBombParticleCount() {
        return config.getInt("effects.bomb-particles.count", 5);
    }

    public int getBombParticleInterval() {
        return config.getInt("effects.bomb-particles.interval-ticks", 20);
    }

    public boolean isBeepSoundEnabled() {
        return config.getBoolean("effects.beep-sound.enabled", true);
    }

    public String getBeepSound() {
        return config.getString("effects.beep-sound.sound", "BLOCK_NOTE_BLOCK_HAT");
    }

    public double getBeepInitialInterval() {
        return config.getDouble("effects.beep-sound.initial-interval", 5);
    }

    public double getBeepFinalInterval() {
        return config.getDouble("effects.beep-sound.final-interval", 0.5);
    }

    // === GETTERS - TITRE ALERTE ===

    public String getPoliceAlertTitle() {
        return config.getString("notifications.police-alert-title.title", "&c&l🚨 CAMBRIOLAGE EN COURS");
    }

    public String getPoliceAlertSubtitle() {
        return config.getString("notifications.police-alert-title.subtitle", "&eUn terrain est attaqué dans votre ville!");
    }

    public int getPoliceAlertFadeIn() {
        return config.getInt("notifications.police-alert-title.fade-in", 10);
    }

    public int getPoliceAlertStay() {
        return config.getInt("notifications.police-alert-title.stay", 100);
    }

    public int getPoliceAlertFadeOut() {
        return config.getInt("notifications.police-alert-title.fade-out", 20);
    }

    /**
     * @return la configuration brute
     */
    public FileConfiguration getConfig() {
        return config;
    }
}
