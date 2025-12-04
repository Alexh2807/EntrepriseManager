package com.gravityyfh.roleplaycity.heist.task;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.heist.config.HeistConfig;
import com.gravityyfh.roleplaycity.heist.data.Heist;
import com.gravityyfh.roleplaycity.heist.data.HeistPhase;
import com.gravityyfh.roleplaycity.heist.manager.HeistManager;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Tâche qui gère le compte à rebours avant l'explosion
 * S'exécute toutes les secondes
 */
public class HeistCountdownTask extends BukkitRunnable {

    private final RoleplayCity plugin;
    private final HeistManager heistManager;
    private final HeistConfig config;
    private final Heist heist;

    private long lastBeepTime;
    private int tickCounter;

    public HeistCountdownTask(RoleplayCity plugin, HeistManager heistManager, Heist heist) {
        this.plugin = plugin;
        this.heistManager = heistManager;
        this.config = heistManager.getConfig();
        this.heist = heist;
        this.lastBeepTime = System.currentTimeMillis();
        this.tickCounter = 0;
    }

    @Override
    public void run() {
        // Vérifier que le heist est toujours en phase COUNTDOWN
        if (heist.getPhase() != HeistPhase.COUNTDOWN) {
            cancel();
            return;
        }

        long remainingSeconds = heist.getCountdownRemainingSeconds();

        // Vérifier si le countdown est terminé
        if (remainingSeconds <= 0) {
            heistManager.onCountdownExpired(heist);
            cancel();
            return;
        }

        // Mettre à jour l'hologramme
        updateHologram(remainingSeconds);

        // Jouer les effets
        playEffects(remainingSeconds);

        // Incrémenter le compteur
        tickCounter++;
    }

    /**
     * Met à jour le texte de l'hologramme avec le temps restant
     */
    private void updateHologram(long remainingSeconds) {
        if (!config.isTimerHologramEnabled()) return;

        Location loc = heist.getBombLocation();
        World world = loc.getWorld();
        if (world == null) return;

        // Formater le temps
        String timeText = formatTime(remainingSeconds);

        // Couleur selon le temps restant
        ChatColor color;
        if (remainingSeconds > 60) {
            color = ChatColor.GREEN;
        } else if (remainingSeconds > 30) {
            color = ChatColor.YELLOW;
        } else if (remainingSeconds > 10) {
            color = ChatColor.GOLD;
        } else {
            color = ChatColor.RED;
        }

        String displayName = color + "💣 " + timeText;

        // Mettre à jour tous les hologrammes enregistrés
        for (UUID hologramId : heist.getHologramEntityIds()) {
            Entity entity = Bukkit.getEntity(hologramId);
            if (entity instanceof ArmorStand) {
                entity.setCustomName(displayName);
            } else {
                // Fallback si l'entité n'est pas trouvée directement (chunk unload/reload)
                for (Entity nearby : world.getNearbyEntities(loc, 4, 4, 4)) {
                    if (nearby.getUniqueId().equals(hologramId)) {
                        nearby.setCustomName(displayName);
                    }
                }
            }
        }
    }

    /**
     * Joue les effets visuels et sonores
     */
    private void playEffects(long remainingSeconds) {
        Location loc = heist.getBombLocation().clone().add(0.5, 1, 0.5);
        World world = loc.getWorld();
        if (world == null) return;

        // Particules
        if (config.areBombParticlesEnabled() && tickCounter % (config.getBombParticleInterval() / 20) == 0) {
            try {
                Particle particle = Particle.valueOf(config.getBombParticleType());
                world.spawnParticle(particle, loc, config.getBombParticleCount(), 0.2, 0.2, 0.2, 0.01);
            } catch (IllegalArgumentException e) {
                world.spawnParticle(Particle.FLAME, loc, 5, 0.2, 0.2, 0.2, 0.01);
            }
        }

        // Son de bip
        if (config.isBeepSoundEnabled()) {
            playBeepSound(remainingSeconds, loc);
        }
    }

    /**
     * Joue le son de bip avec intervalle qui accélère
     */
    private void playBeepSound(long remainingSeconds, Location loc) {
        double interval;

        // L'intervalle diminue quand on approche de 0
        if (remainingSeconds > 60) {
            interval = config.getBeepInitialInterval();
        } else if (remainingSeconds > 30) {
            // Interpolation linéaire entre initial et final
            double progress = (60 - remainingSeconds) / 30.0;
            interval = config.getBeepInitialInterval() -
                (config.getBeepInitialInterval() - 1.0) * progress;
        } else if (remainingSeconds > 10) {
            interval = 1.0;
        } else if (remainingSeconds > 5) {
            interval = 0.5;
        } else {
            interval = config.getBeepFinalInterval();
        }

        // Vérifier si c'est le moment de jouer un bip
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastBeepTime >= interval * 1000) {
            lastBeepTime = currentTime;

            // Jouer le son
            try {
                Sound sound = Sound.valueOf(config.getBeepSound());

                // Pitch qui monte quand le temps diminue
                float pitch = 1.0f + (1.0f - (float) remainingSeconds / heist.getCountdownSeconds());
                pitch = Math.min(2.0f, pitch);

                loc.getWorld().playSound(loc, sound, 1.0f, pitch);
            } catch (IllegalArgumentException e) {
                loc.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Formate le temps en "M:SS"
     */
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return String.format("%d:%02d", minutes, secs);
    }
}
