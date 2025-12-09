package com.gravityyfh.roleplaycity.town.task;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche de gestion du cycle jour/nuit personnalisé
 * Permet de configurer:
 * - L'heure de début du jour (ex: 10:00)
 * - L'heure de début de la nuit (ex: 19:00)
 * - La durée IRL du jour et de la nuit
 *
 * Le système mappe les heures configurées sur le cycle lumineux de Minecraft:
 * - Heure de début jour → Lever du soleil complet (ticks ~1000)
 * - Heure de début nuit → Coucher du soleil (ticks ~13000)
 */
public class DayNightCycleTask extends BukkitRunnable {

    private final RoleplayCity plugin;
    private final String worldName;

    // Configuration des heures (en heures 0-24)
    private final int dayStartHour;    // Heure de début du jour (ex: 10)
    private final int nightStartHour;  // Heure de début de la nuit (ex: 19)

    // Durées configurées (en minutes IRL)
    private final int dayDurationMinutes;
    private final int nightDurationMinutes;

    // Ticks Minecraft pour les phases lumineuses
    // Note: On utilise les ticks MC pour la luminosité, pas pour l'heure affichée
    private static final long MC_DAY_START_TICKS = 1000;   // Soleil complètement levé
    private static final long MC_NIGHT_START_TICKS = 13000; // Nuit commence
    private static final long MC_CYCLE_TICKS = 24000;      // Cycle complet

    // Intervalle de mise à jour (en ticks serveur) - toutes les secondes
    private static final long UPDATE_INTERVAL = 20L;

    // Variables de suivi
    private long lastUpdateTime;
    private boolean enabled;

    // Heure virtuelle (en secondes depuis minuit, 0-86400)
    private double virtualTimeSeconds;

    public DayNightCycleTask(RoleplayCity plugin) {
        this.plugin = plugin;

        // Charger la configuration
        this.worldName = plugin.getConfig().getString("day-night-cycle.world", "world");
        this.dayStartHour = plugin.getConfig().getInt("day-night-cycle.day-start-hour", 10);
        this.nightStartHour = plugin.getConfig().getInt("day-night-cycle.night-start-hour", 19);
        this.dayDurationMinutes = plugin.getConfig().getInt("day-night-cycle.day-duration-minutes", 50);
        this.nightDurationMinutes = plugin.getConfig().getInt("day-night-cycle.night-duration-minutes", 10);
        this.enabled = plugin.getConfig().getBoolean("day-night-cycle.enabled", false);

        this.lastUpdateTime = System.currentTimeMillis();

        // Initialiser l'heure virtuelle à partir du temps Minecraft actuel
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            this.virtualTimeSeconds = mcTicksToVirtualSeconds(world.getTime());
        } else {
            this.virtualTimeSeconds = dayStartHour * 3600.0; // Commencer au début du jour
        }
    }

    @Override
    public void run() {
        if (!enabled) return;

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[DayNight] Monde '" + worldName + "' introuvable!");
            return;
        }

        // Calculer le temps écoulé depuis la dernière mise à jour
        long currentTime = System.currentTimeMillis();
        long elapsedMs = currentTime - lastUpdateTime;
        lastUpdateTime = currentTime;

        // Calculer combien de secondes virtuelles se sont écoulées
        double virtualSecondsToAdd = calculateVirtualSecondsToAdd(elapsedMs);

        // Avancer l'heure virtuelle
        virtualTimeSeconds += virtualSecondsToAdd;

        // Boucler sur 24h (86400 secondes)
        if (virtualTimeSeconds >= 86400) {
            virtualTimeSeconds -= 86400;
        }

        // Convertir l'heure virtuelle en ticks Minecraft et appliquer
        long mcTicks = virtualSecondsToMcTicks(virtualTimeSeconds);
        world.setTime(mcTicks);
    }

    /**
     * Calcule combien de secondes virtuelles s'écoulent pour un temps IRL donné
     * Dépend de si on est dans la phase jour ou nuit
     */
    private double calculateVirtualSecondsToAdd(long elapsedMs) {
        // Durée virtuelle des phases (en secondes virtuelles)
        int dayDurationVirtualSeconds = (nightStartHour - dayStartHour) * 3600; // Ex: (19-10)*3600 = 32400s
        int nightDurationVirtualSeconds = 86400 - dayDurationVirtualSeconds;     // Ex: 86400-32400 = 54000s

        // Durée IRL des phases (en millisecondes)
        long dayDurationIrlMs = dayDurationMinutes * 60L * 1000L;
        long nightDurationIrlMs = nightDurationMinutes * 60L * 1000L;

        // Déterminer si on est en phase jour ou nuit
        int currentHour = (int) (virtualTimeSeconds / 3600);
        boolean isDay = currentHour >= dayStartHour && currentHour < nightStartHour;

        // Calculer le ratio secondes virtuelles / millisecondes IRL
        double ratio;
        if (isDay) {
            ratio = (double) dayDurationVirtualSeconds / dayDurationIrlMs;
        } else {
            ratio = (double) nightDurationVirtualSeconds / nightDurationIrlMs;
        }

        return elapsedMs * ratio;
    }

    /**
     * Convertit des secondes virtuelles (0-86400) en ticks Minecraft (0-24000)
     * en respectant le mapping jour/nuit configuré
     */
    private long virtualSecondsToMcTicks(double virtualSeconds) {
        int virtualHour = (int) (virtualSeconds / 3600);
        double virtualMinutesFraction = (virtualSeconds % 3600) / 60.0;

        // Durée des phases en heures virtuelles
        int dayHours = nightStartHour - dayStartHour;   // Ex: 19-10 = 9h de jour
        int nightHours = 24 - dayHours;                  // Ex: 24-9 = 15h de nuit

        // Durée des phases en ticks MC
        long mcDayTicks = MC_NIGHT_START_TICKS - MC_DAY_START_TICKS;  // 12000 ticks de jour
        long mcNightTicks = MC_CYCLE_TICKS - mcDayTicks;              // 12000 ticks de nuit

        if (virtualHour >= dayStartHour && virtualHour < nightStartHour) {
            // Phase JOUR: mapper [dayStartHour..nightStartHour] sur [MC_DAY_START_TICKS..MC_NIGHT_START_TICKS]
            double hoursIntoDayPhase = (virtualHour - dayStartHour) + (virtualMinutesFraction / 60.0);
            double dayProgress = hoursIntoDayPhase / dayHours;
            return MC_DAY_START_TICKS + (long) (dayProgress * mcDayTicks);
        } else {
            // Phase NUIT: mapper les heures de nuit sur [MC_NIGHT_START_TICKS..MC_DAY_START_TICKS+24000]
            double hoursIntoNightPhase;
            if (virtualHour >= nightStartHour) {
                hoursIntoNightPhase = (virtualHour - nightStartHour) + (virtualMinutesFraction / 60.0);
            } else {
                // Après minuit, avant dayStartHour
                hoursIntoNightPhase = (24 - nightStartHour + virtualHour) + (virtualMinutesFraction / 60.0);
            }
            double nightProgress = hoursIntoNightPhase / nightHours;
            long ticks = MC_NIGHT_START_TICKS + (long) (nightProgress * mcNightTicks);
            return ticks % MC_CYCLE_TICKS;
        }
    }

    /**
     * Convertit des ticks Minecraft en secondes virtuelles
     * (utilisé pour initialiser l'heure virtuelle au démarrage)
     */
    private double mcTicksToVirtualSeconds(long mcTicks) {
        // Simplification: on commence à l'heure de début du jour
        return dayStartHour * 3600.0;
    }

    /**
     * Démarre la tâche de gestion du cycle jour/nuit
     */
    public void start() {
        if (!enabled) {
            plugin.getLogger().info("[DayNight] Cycle jour/nuit personnalisé désactivé dans la config.");
            return;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[DayNight] Monde '" + worldName + "' introuvable! Tâche non démarrée.");
            return;
        }

        // Désactiver le cycle naturel de Minecraft
        world.setGameRuleValue("doDaylightCycle", "false");

        // Démarrer la tâche
        this.runTaskTimer(plugin, UPDATE_INTERVAL, UPDATE_INTERVAL);

        plugin.getLogger().info("[DayNight] Cycle jour/nuit personnalisé activé!");
        plugin.getLogger().info("[DayNight] - Monde: " + worldName);
        plugin.getLogger().info("[DayNight] - Début du jour: " + dayStartHour + "h00");
        plugin.getLogger().info("[DayNight] - Début de la nuit: " + nightStartHour + "h00");
        plugin.getLogger().info("[DayNight] - Durée jour IRL: " + dayDurationMinutes + " minutes");
        plugin.getLogger().info("[DayNight] - Durée nuit IRL: " + nightDurationMinutes + " minutes");
        plugin.getLogger().info("[DayNight] - Cycle total IRL: " + (dayDurationMinutes + nightDurationMinutes) + " minutes");
    }

    /**
     * Arrête la tâche et restaure le cycle naturel
     */
    public void stop() {
        try {
            this.cancel();
        } catch (IllegalStateException ignored) {
            // Task pas encore démarrée
        }

        if (enabled) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                // Restaurer le cycle naturel
                world.setGameRuleValue("doDaylightCycle", "true");
                plugin.getLogger().info("[DayNight] Cycle naturel Minecraft restauré.");
            }
        }
    }

    /**
     * Vérifie si le cycle personnalisé est activé
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Active ou désactive le cycle personnalisé
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;

        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            world.setGameRuleValue("doDaylightCycle", enabled ? "false" : "true");
        }
    }

    /**
     * Force l'heure virtuelle (0-24 en heures)
     */
    public void setVirtualHour(int hour) {
        this.virtualTimeSeconds = (hour % 24) * 3600.0;
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Force l'heure virtuelle avec minutes (HH:MM)
     */
    public void setVirtualTime(int hour, int minute) {
        this.virtualTimeSeconds = ((hour % 24) * 3600.0) + (minute * 60.0);
        lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Force le jour (début de la phase jour)
     */
    public void setDay() {
        setVirtualHour(dayStartHour);
    }

    /**
     * Force la nuit (début de la phase nuit)
     */
    public void setNight() {
        setVirtualHour(nightStartHour);
    }

    /**
     * Obtient l'heure virtuelle actuelle formatée (HH:MM)
     * C'est l'heure "roleplay" affichée aux joueurs
     */
    public String getFormattedTime() {
        int hours = (int) (virtualTimeSeconds / 3600);
        int minutes = (int) ((virtualTimeSeconds % 3600) / 60);
        return String.format("%02d:%02d", hours, minutes);
    }

    /**
     * Obtient l'heure virtuelle actuelle (0-23)
     */
    public int getCurrentHour() {
        return (int) (virtualTimeSeconds / 3600);
    }

    /**
     * Obtient les minutes virtuelles actuelles (0-59)
     */
    public int getCurrentMinute() {
        return (int) ((virtualTimeSeconds % 3600) / 60);
    }

    /**
     * Vérifie si c'est actuellement le jour (phase jour)
     */
    public boolean isDay() {
        int currentHour = getCurrentHour();
        return currentHour >= dayStartHour && currentHour < nightStartHour;
    }

    /**
     * Vérifie si c'est actuellement la nuit (phase nuit)
     */
    public boolean isNight() {
        return !isDay();
    }

    /**
     * Obtient l'heure de début du jour
     */
    public int getDayStartHour() {
        return dayStartHour;
    }

    /**
     * Obtient l'heure de début de la nuit
     */
    public int getNightStartHour() {
        return nightStartHour;
    }

    /**
     * Obtient le nom du monde géré
     */
    public String getWorldName() {
        return worldName;
    }
}
