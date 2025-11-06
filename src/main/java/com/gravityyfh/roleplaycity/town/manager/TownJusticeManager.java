package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Gestionnaire du système de justice des villes
 * Gère les contestations et les jugements
 */
public class TownJusticeManager {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownPoliceManager policeManager;

    public TownJusticeManager(RoleplayCity plugin, TownManager townManager, TownPoliceManager policeManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.policeManager = policeManager;
    }

    /**
     * Juger une contestation d'amende
     */
    public boolean judgeFine(Fine fine, Player judge, boolean valid, String verdict) {
        Town town = townManager.getTown(fine.getTownName());
        if (town == null) {
            return false;
        }

        // Vérifier que le juge a le rôle approprié
        TownRole role = town.getMemberRole(judge.getUniqueId());
        if (role != TownRole.JUGE && role != TownRole.MAIRE) {
            judge.sendMessage(ChatColor.RED + "Vous n'avez pas l'autorité pour juger cette affaire.");
            return false;
        }

        // Vérifier que l'amende est bien contestée
        if (!fine.isContested()) {
            judge.sendMessage(ChatColor.RED + "Cette amende n'est pas contestée.");
            return false;
        }

        // Enregistrer le jugement
        fine.setJudgeVerdict(judge.getUniqueId(), valid, verdict);

        // Notification au contrevenant
        if (valid) {
            // Contestation rejetée
            plugin.getNotificationManager().sendNotification(
                fine.getOffenderUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.WARNING,
                "Contestation rejetée",
                String.format("Le juge %s a rejeté votre contestation. Amende: %.2f€. Verdict: %s",
                    judge.getName(), fine.getAmount(), verdict)
            );
        } else {
            // Contestation acceptée
            plugin.getNotificationManager().sendNotification(
                fine.getOffenderUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
                "Contestation acceptée !",
                String.format("Le juge %s a accepté votre contestation. L'amende de %.2f€ est annulée ! Verdict: %s",
                    judge.getName(), fine.getAmount(), verdict)
            );
        }

        // Notification au policier qui a émis l'amende
        if (valid) {
            plugin.getNotificationManager().sendNotification(
                fine.getPolicierUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
                "Amende confirmée",
                String.format("Le juge %s a confirmé votre amende de %.2f€ contre %s.",
                    judge.getName(), fine.getAmount(), fine.getOffenderName())
            );
        } else {
            plugin.getNotificationManager().sendNotification(
                fine.getPolicierUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.WARNING,
                "Amende annulée",
                String.format("Le juge %s a annulé votre amende de %.2f€ contre %s. Verdict: %s",
                    judge.getName(), fine.getAmount(), fine.getOffenderName(), verdict)
            );
        }

        judge.sendMessage(ChatColor.GREEN + "Jugement enregistré avec succès.");

        plugin.getLogger().info("Jugement rendu par " + judge.getName() + ": Amende " +
            (valid ? "confirmée" : "annulée") + " - " + fine);

        // Sauvegarder immédiatement
        plugin.getTownFinesDataManager().saveFines(policeManager.getFinesForSave());

        return true;
    }

    /**
     * Obtenir un résumé d'une amende pour le juge
     */
    public String getFineReview(Fine fine) {
        StringBuilder sb = new StringBuilder();
        sb.append(ChatColor.GOLD).append("=== DOSSIER D'AMENDE ===\n");
        sb.append(ChatColor.GRAY).append("ID: ").append(ChatColor.WHITE)
            .append(fine.getFineId().toString().substring(0, 8)).append("\n");
        sb.append(ChatColor.GRAY).append("Contrevenant: ").append(ChatColor.YELLOW)
            .append(fine.getOffenderName()).append("\n");
        sb.append(ChatColor.GRAY).append("Policier: ").append(ChatColor.YELLOW)
            .append(fine.getPolicierName()).append("\n");
        sb.append(ChatColor.GRAY).append("Motif: ").append(ChatColor.WHITE)
            .append(fine.getReason()).append("\n");
        sb.append(ChatColor.GRAY).append("Montant: ").append(ChatColor.GOLD)
            .append(fine.getAmount()).append("€\n");
        sb.append(ChatColor.GRAY).append("Date: ").append(ChatColor.WHITE)
            .append(fine.getIssueDate().toLocalDate()).append("\n");
        sb.append(ChatColor.GRAY).append("Statut: ").append(ChatColor.AQUA)
            .append(fine.getStatus().getDisplayName()).append("\n");

        return sb.toString();
    }

    /**
     * Vérifier si un joueur peut juger (est juge ou maire)
     */
    public boolean canJudge(Player player, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return false;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());
        return role == TownRole.JUGE || role == TownRole.MAIRE;
    }

    /**
     * Statistiques de justice d'une ville
     */
    public JusticeStatistics getTownStatistics(String townName) {
        var fines = policeManager.getTownFines(townName);

        long totalJudgements = fines.stream()
            .filter(f -> f.getStatus() == Fine.FineStatus.JUDGED_VALID ||
                        f.getStatus() == Fine.FineStatus.JUDGED_INVALID)
            .count();

        long confirmedJudgements = fines.stream()
            .filter(f -> f.getStatus() == Fine.FineStatus.JUDGED_VALID)
            .count();

        long cancelledJudgements = fines.stream()
            .filter(f -> f.getStatus() == Fine.FineStatus.JUDGED_INVALID)
            .count();

        long pendingContestations = fines.stream()
            .filter(Fine::isContested)
            .count();

        return new JusticeStatistics(totalJudgements, confirmedJudgements,
            cancelledJudgements, pendingContestations);
    }

    public static class JusticeStatistics {
        public final long totalJudgements;
        public final long confirmedJudgements;
        public final long cancelledJudgements;
        public final long pendingContestations;

        public JusticeStatistics(long totalJudgements, long confirmedJudgements,
                               long cancelledJudgements, long pendingContestations) {
            this.totalJudgements = totalJudgements;
            this.confirmedJudgements = confirmedJudgements;
            this.cancelledJudgements = cancelledJudgements;
            this.pendingContestations = pendingContestations;
        }

        public double getConfirmationRate() {
            if (totalJudgements == 0) return 0;
            return (double) confirmedJudgements / totalJudgements * 100;
        }
    }
}
