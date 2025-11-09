package com.gravityyfh.roleplaycity.medical.data;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Représente un processus de soin en cours
 */
public class HealingProcess {
    private final UUID medicUuid;
    private final UUID patientUuid;
    private final Player medic;
    private final Player patient;
    private final BossBar progressBar;

    private int progressSeconds;
    private int taskId = -1;
    private boolean completed = false;
    private boolean interrupted = false;
    private boolean paused = false; // Pour mettre en pause pendant les mini-jeux

    // Durée totale du processus de soin (30 secondes)
    private static final int TOTAL_DURATION = 30;

    public HealingProcess(Player medic, Player patient, BossBar progressBar) {
        this.medic = medic;
        this.patient = patient;
        this.medicUuid = medic.getUniqueId();
        this.patientUuid = patient.getUniqueId();
        this.progressBar = progressBar;
        this.progressSeconds = 0;
    }

    public UUID getMedicUuid() {
        return medicUuid;
    }

    public UUID getPatientUuid() {
        return patientUuid;
    }

    public Player getMedic() {
        return medic;
    }

    public Player getPatient() {
        return patient;
    }

    public BossBar getProgressBar() {
        return progressBar;
    }

    public int getProgressSeconds() {
        return progressSeconds;
    }

    public void incrementProgress() {
        this.progressSeconds++;
    }

    public double getProgressPercentage() {
        return (double) progressSeconds / TOTAL_DURATION;
    }

    public boolean isCompleted() {
        return progressSeconds >= TOTAL_DURATION || completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public int getTotalDuration() {
        return TOTAL_DURATION;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Retourne le message RP en fonction de la progression (30 secondes)
     */
    public String getRPMessage() {
        if (progressSeconds <= 5) {
            return "🩺 Examen des blessures...";
        } else if (progressSeconds <= 10) {
            return "💊 Préparation des médicaments...";
        } else if (progressSeconds <= 15) {
            return "💉 Administration des injections...";
        } else if (progressSeconds <= 20) {
            return "🩹 Bandages et pansements...";
        } else if (progressSeconds <= 25) {
            return "❤️ Stabilisation du patient...";
        } else {
            return "✅ Finalisation des soins...";
        }
    }

    /**
     * Retourne une action RP spécifique pour le médecin
     */
    public String getMedicAction() {
        if (progressSeconds == 3) {
            return "§e➤ Vérification des constantes vitales...";
        } else if (progressSeconds == 7) {
            return "§e➤ Préparation du matériel médical...";
        } else if (progressSeconds == 12) {
            return "§e➤ Injection d'analgésiques...";
        } else if (progressSeconds == 17) {
            return "§e➤ Désinfection des plaies...";
        } else if (progressSeconds == 22) {
            return "§e➤ Pose de bandages compressifs...";
        } else if (progressSeconds == 27) {
            return "§e➤ Surveillance des signes vitaux...";
        }
        return null;
    }

    /**
     * Retourne un message pour le patient
     */
    public String getPatientMessage() {
        if (progressSeconds == 5) {
            return "§7Vous sentez une présence rassurante...";
        } else if (progressSeconds == 10) {
            return "§7La douleur commence à s'atténuer...";
        } else if (progressSeconds == 15) {
            return "§7Vous ressentez un soulagement progressif...";
        } else if (progressSeconds == 20) {
            return "§7Votre respiration se stabilise...";
        } else if (progressSeconds == 25) {
            return "§aVous reprenez doucement vos esprits...";
        }
        return null;
    }

    /**
     * Vérifie si c'est un moment où un mini-jeu doit être lancé
     * @return la difficulté du mini-jeu (nombre de points), ou 0 si pas de mini-jeu
     */
    public int shouldStartMinigame() {
        if (progressSeconds == 8) {
            return 3; // 3 points de suture - Facile
        } else if (progressSeconds == 18) {
            return 5; // 5 points de suture - Moyen
        } else if (progressSeconds == 28) {
            return 4; // 4 points de suture - Facile (dernière étape)
        }
        return 0;
    }
}
