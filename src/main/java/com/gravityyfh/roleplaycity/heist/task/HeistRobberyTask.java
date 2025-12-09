package com.gravityyfh.roleplaycity.heist.task;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.heist.config.HeistConfig;
import com.gravityyfh.roleplaycity.heist.data.Heist;
import com.gravityyfh.roleplaycity.heist.data.HeistParticipant;
import com.gravityyfh.roleplaycity.heist.data.HeistPhase;
import com.gravityyfh.roleplaycity.heist.manager.HeistManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

/**
 * Tâche qui gère la phase de vol après l'explosion
 * S'exécute toutes les secondes
 */
public class HeistRobberyTask extends BukkitRunnable {

    private final RoleplayCity plugin;
    private final HeistManager heistManager;
    private final HeistConfig config;
    private final Heist heist;

    private final BossBar bossBar;
    private int tickCounter;

    public HeistRobberyTask(RoleplayCity plugin, HeistManager heistManager, Heist heist) {
        this.plugin = plugin;
        this.heistManager = heistManager;
        this.config = heistManager.getConfig();
        this.heist = heist;
        this.tickCounter = 0;

        // Créer la boss bar pour les participants
        this.bossBar = Bukkit.createBossBar(
            ChatColor.RED + "🔓 VOL EN COURS",
            BarColor.RED,
            BarStyle.SEGMENTED_10
        );
        bossBar.setVisible(true);

        // Ajouter tous les participants à la boss bar
        for (UUID participantId : heist.getParticipants().keySet()) {
            Player player = Bukkit.getPlayer(participantId);
            if (player != null && player.isOnline()) {
                bossBar.addPlayer(player);
            }
        }
    }

    @Override
    public void run() {
        // Vérifier que le heist est toujours en phase ROBBERY
        if (heist.getPhase() != HeistPhase.ROBBERY) {
            cleanup();
            cancel();
            return;
        }

        long remainingSeconds = heist.getRobberyRemainingSeconds();

        // Vérifier si le temps est écoulé
        if (remainingSeconds <= 0) {
            cleanup();
            heistManager.onRobberyExpired(heist);
            cancel();
            return;
        }

        // Mettre à jour la boss bar
        updateBossBar(remainingSeconds);

        // Vérifier les déconnexions expirées
        checkDisconnectedParticipants();

        // Vérifier si tous les participants sont hors-jeu
        heistManager.checkAllParticipantsOut(heist);

        // Messages de rappel à certains intervalles
        sendReminderMessages(remainingSeconds);

        tickCounter++;
    }

    /**
     * Met à jour la boss bar avec le temps restant
     */
    private void updateBossBar(long remainingSeconds) {
        // Calculer le pourcentage
        double progress = (double) remainingSeconds / heist.getRobberySeconds();
        bossBar.setProgress(Math.max(0, Math.min(1, progress)));

        // Changer la couleur selon le temps restant
        if (remainingSeconds > 120) {
            bossBar.setColor(BarColor.GREEN);
        } else if (remainingSeconds > 60) {
            bossBar.setColor(BarColor.YELLOW);
        } else if (remainingSeconds > 30) {
            bossBar.setColor(BarColor.RED);
        } else {
            // Clignotement dans les dernières secondes
            bossBar.setColor(tickCounter % 2 == 0 ? BarColor.RED : BarColor.WHITE);
        }

        // Mettre à jour le titre
        String timeFormatted = formatTime(remainingSeconds);
        bossBar.setTitle(ChatColor.RED + "🔓 VOL EN COURS - " + ChatColor.YELLOW + timeFormatted);
    }

    /**
     * Vérifie les participants déconnectés dont la grâce a expiré
     */
    private void checkDisconnectedParticipants() {
        int graceMinutes = config.getDisconnectGraceMinutes();

        for (HeistParticipant participant : heist.getParticipants().values()) {
            if (participant.getStatus() == HeistParticipant.ParticipantStatus.DISCONNECTED) {
                if (participant.isDisconnectGraceExpired(graceMinutes)) {
                    participant.setStatus(HeistParticipant.ParticipantStatus.GRACE_EXPIRED);
                    plugin.getLogger().info("[Heist] Participant " + participant.getPlayerName()
                        + " a expiré sa période de grâce de déconnexion");
                }
            }
        }
    }

    /**
     * Envoie des messages de rappel aux participants
     */
    private void sendReminderMessages(long remainingSeconds) {
        // Messages à 2 minutes, 1 minute, 30 secondes, 10 secondes
        if (remainingSeconds == 120 || remainingSeconds == 60 ||
            remainingSeconds == 30 || remainingSeconds == 10) {

            String message = ChatColor.YELLOW + "⏱ " + formatTime(remainingSeconds)
                + " restantes pour voler!";

            for (UUID participantId : heist.getParticipants().keySet()) {
                HeistParticipant participant = heist.getParticipant(participantId);
                if (participant.canAct()) {
                    Player player = Bukkit.getPlayer(participantId);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(message);
                    }
                }
            }
        }
    }

    /**
     * Nettoie les ressources à la fin
     */
    private void cleanup() {
        bossBar.removeAll();
        bossBar.setVisible(false);
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
