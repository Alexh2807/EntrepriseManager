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

        // Récupérer les paramètres de configuration
        double judgeCommissionPercentage = plugin.getConfig().getDouble("town.commissions.judge-commission-percentage", 50.0);
        double courtFeesPercentage = plugin.getConfig().getDouble("town.commissions.court-fees-percentage", 30.0);
        double policeCommissionPercentage = plugin.getConfig().getDouble("town.commissions.police-commission-percentage", 50.0);

        // Calculer les frais de justice
        double courtFees = fine.getAmount() * (courtFeesPercentage / 100.0);
        double judgeCommission = courtFees * (judgeCommissionPercentage / 100.0);
        double townShareFromCourtFees = courtFees - judgeCommission;

        if (valid) {
            // CAS 1: Juge CONFIRME l'amende
            // Le contrevenant doit payer: Amende + Frais de justice

            Player offender = Bukkit.getPlayer(fine.getOffenderUuid());
            double totalToPay = fine.getAmount() + courtFees;

            // Vérifier que le contrevenant a assez d'argent
            if (offender != null && offender.isOnline()) {
                if (RoleplayCity.getEconomy().has(offender, totalToPay)) {
                    // Prélever l'argent total
                    RoleplayCity.getEconomy().withdrawPlayer(offender, totalToPay);

                    // Répartition de l'amende (50/50 policier/ville)
                    double policeCommission = fine.getAmount() * (policeCommissionPercentage / 100.0);
                    double townShareFromFine = fine.getAmount() - policeCommission;

                    // Verser la commission au policier
                    RoleplayCity.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(fine.getPolicierUuid()), policeCommission);

                    // Notification au policier
                    plugin.getNotificationManager().sendNotification(
                        fine.getPolicierUuid(),
                        com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.ECONOMY,
                        "Commission reçue",
                        String.format("Amende confirmée par le juge ! Vous recevez %.2f€ de commission.",
                            policeCommission)
                    );

                    // Verser la commission au juge
                    RoleplayCity.getEconomy().depositPlayer(judge, judgeCommission);

                    // Verser à la ville
                    if (town != null) {
                        town.deposit(townShareFromFine + townShareFromCourtFees);
                    }

                    // Notification au juge
                    plugin.getNotificationManager().sendNotification(
                        judge.getUniqueId(),
                        com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.ECONOMY,
                        "Commission de jugement",
                        String.format("Vous avez reçu %.2f€ pour avoir jugé cette affaire.", judgeCommission)
                    );

                    plugin.getLogger().info(String.format("Jugement confirmé: Contrevenant payé %.2f€ (Amende: %.2f€ + Frais: %.2f€). Policier: %.2f€, Juge: %.2f€, Ville: %.2f€",
                        totalToPay, fine.getAmount(), courtFees, policeCommission, judgeCommission, (townShareFromFine + townShareFromCourtFees)));
                } else {
                    offender.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent pour payer l'amende et les frais de justice (" + totalToPay + "€)");
                }
            }

            // Notification au contrevenant
            plugin.getNotificationManager().sendNotification(
                fine.getOffenderUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.WARNING,
                "Contestation rejetée",
                String.format("Le juge %s a rejeté votre contestation. Total à payer: %.2f€ (Amende: %.2f€ + Frais de justice: %.2f€). Verdict: %s",
                    judge.getName(), totalToPay, fine.getAmount(), courtFees, verdict)
            );

        } else {
            // CAS 2: Juge ANNULE l'amende
            // Le policier paie la prime du juge

            Player policier = Bukkit.getPlayer(fine.getPolicierUuid());

            // Prélever au policier (même si négatif)
            if (policier != null && policier.isOnline()) {
                RoleplayCity.getEconomy().withdrawPlayer(policier, judgeCommission);
            } else {
                RoleplayCity.getEconomy().withdrawPlayer(Bukkit.getOfflinePlayer(fine.getPolicierUuid()), judgeCommission);
            }

            // Verser au juge
            RoleplayCity.getEconomy().depositPlayer(judge, judgeCommission);

            // Notification au policier
            plugin.getNotificationManager().sendNotification(
                fine.getPolicierUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.WARNING,
                "Amende annulée - Pénalité",
                String.format("Le juge %s a annulé votre amende de %.2f€ contre %s. Vous payez %.2f€ au juge. Verdict: %s",
                    judge.getName(), fine.getAmount(), fine.getOffenderName(), judgeCommission, verdict)
            );

            // Notification au juge
            plugin.getNotificationManager().sendNotification(
                judge.getUniqueId(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.ECONOMY,
                "Commission de jugement",
                String.format("Vous avez reçu %.2f€ pour avoir jugé cette affaire.", judgeCommission)
            );

            // Notification au contrevenant
            plugin.getNotificationManager().sendNotification(
                fine.getOffenderUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
                "Contestation acceptée !",
                String.format("Le juge %s a accepté votre contestation. L'amende de %.2f€ est annulée ! Verdict: %s",
                    judge.getName(), fine.getAmount(), verdict)
            );

            plugin.getLogger().info(String.format("Jugement annulé: Policier paie %.2f€ au juge.", judgeCommission));
        }

        judge.sendMessage(ChatColor.GREEN + "Jugement enregistré avec succès.");

        plugin.getLogger().info("Jugement rendu par " + judge.getName() + ": Amende " +
            (valid ? "confirmée" : "annulée") + " - " + fine);

        // Sauvegarder immédiatement (amendes + banque ville)
        plugin.getTownFinesDataManager().saveFines(policeManager.getFinesForSave());
        townManager.saveTownsNow();

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
