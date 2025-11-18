package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

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
            judge.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            judge.sendMessage("§c✖ Accès refusé");
            judge.sendMessage("§7Vous n'avez pas l'autorité pour juger");
            judge.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Vérifier que l'amende est bien contestée
        if (!fine.isContested()) {
            judge.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            judge.sendMessage("§c✖ Cette amende n'est pas contestée");
            judge.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
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
            // Le contrevenant paie automatiquement: Amende + Frais de justice (MÊME SI NÉGATIF)

            double totalToPay = fine.getAmount() + courtFees;

            // Prélever l'argent total (TOUJOURS, même si fonds insuffisants = négatif)
            RoleplayCity.getEconomy().withdrawPlayer(Bukkit.getOfflinePlayer(fine.getOffenderUuid()), totalToPay);

            // Répartition de l'amende (50/50 policier/ville)
            double policeCommission = fine.getAmount() * (policeCommissionPercentage / 100.0);
            double townShareFromFine = fine.getAmount() - policeCommission;

            // Verser la commission au policier
            RoleplayCity.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(fine.getPolicierUuid()), policeCommission);

            // Notification au policier
            plugin.getNotificationManager().sendNotification(
                fine.getPolicierUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.ECONOMY,
                "⚖ JUGEMENT: CONFIRMÉ",
                String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a§lVotre amende confirmée\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Juge: §d%s\n§7Contrevenant: §e%s\n§7Commission: §6+%.2f€\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a✔ Amende justifiée",
                    judge.getName(), fine.getOffenderName(), policeCommission)
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
                "⚖ HONORAIRES DE JUGEMENT",
                String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§d§lCommission reçue\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Affaire jugée avec succès\n§7Honoraires: §6+%.2f€\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§d⚖ Jugement rendu",
                    judgeCommission)
            );

            // Marquer l'amende comme payée
            fine.markAsPaid();

            // Notification au contrevenant
            plugin.getNotificationManager().sendNotification(
                fine.getOffenderUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.WARNING,
                "⚖ CONTESTATION REJETÉE",
                String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§c§lJugement défavorable\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Juge: §d%s\n§7Amende: §6%.2f€\n§7Frais de justice: §6+%.2f€\n§7Total payé: §c-%.2f€\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Verdict: §f%s\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a✔ Paiement effectué automatiquement",
                    judge.getName(), fine.getAmount(), courtFees, totalToPay, verdict)
            );

            plugin.getLogger().info(String.format("Jugement confirmé: Contrevenant payé %.2f€ (Amende: %.2f€ + Frais: %.2f€). Policier: %.2f€, Juge: %.2f€, Ville: %.2f€",
                totalToPay, fine.getAmount(), courtFees, policeCommission, judgeCommission, (townShareFromFine + townShareFromCourtFees)));

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
                "⚖ JUGEMENT: ANNULÉ",
                String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§c§lAmende injustifiée\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Juge: §d%s\n§7Contrevenant: §e%s\n§7Amende initiale: §6%.2f€\n§7Pénalité versée: §c-%.2f€\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Verdict: §f%s\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§c⚠ Amende abusive - Pénalité appliquée",
                    judge.getName(), fine.getOffenderName(), fine.getAmount(), judgeCommission, verdict)
            );

            // Notification au juge
            plugin.getNotificationManager().sendNotification(
                judge.getUniqueId(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.ECONOMY,
                "⚖ HONORAIRES DE JUGEMENT",
                String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§d§lCommission reçue\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Affaire jugée avec succès\n§7Honoraires: §6+%.2f€\n§7Source: §ePénalité policier\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§d⚖ Jugement rendu",
                    judgeCommission)
            );

            // Notification au contrevenant
            plugin.getNotificationManager().sendNotification(
                fine.getOffenderUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
                "⚖ CONTESTATION ACCEPTÉE",
                String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a§lJugement favorable\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Juge: §d%s\n§7Amende annulée: §6%.2f€\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Verdict: §f%s\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a✔ Vous ne devez rien payer",
                    judge.getName(), fine.getAmount(), verdict)
            );

            plugin.getLogger().info(String.format("Jugement annulé: Policier paie %.2f€ au juge.", judgeCommission));
        }

        judge.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        judge.sendMessage("§d⚖ §lJUGEMENT ENREGISTRÉ");
        judge.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        judge.sendMessage("§7Décision: " + (valid ? "§aConfirmation" : "§cAnnulation"));
        judge.sendMessage("§7Montant: §6" + fine.getAmount() + "€");
        judge.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

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
        String sb = ChatColor.GOLD + "=== DOSSIER D'AMENDE ===\n" +
                ChatColor.GRAY + "ID: " + ChatColor.WHITE +
                fine.getFineId().toString().substring(0, 8) + "\n" +
                ChatColor.GRAY + "Contrevenant: " + ChatColor.YELLOW +
                fine.getOffenderName() + "\n" +
                ChatColor.GRAY + "Policier: " + ChatColor.YELLOW +
                fine.getPolicierName() + "\n" +
                ChatColor.GRAY + "Motif: " + ChatColor.WHITE +
                fine.getReason() + "\n" +
                ChatColor.GRAY + "Montant: " + ChatColor.GOLD +
                fine.getAmount() + "€\n" +
                ChatColor.GRAY + "Date: " + ChatColor.WHITE +
                fine.getIssueDate().toLocalDate() + "\n" +
                ChatColor.GRAY + "Statut: " + ChatColor.AQUA +
                fine.getStatus().getDisplayName() + "\n";

        return sb;
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

    public record JusticeStatistics(long totalJudgements, long confirmedJudgements, long cancelledJudgements,
                                    long pendingContestations) {

        public double getConfirmationRate() {
                if (totalJudgements == 0) return 0;
                return (double) confirmedJudgements / totalJudgements * 100;
            }
        }
}
