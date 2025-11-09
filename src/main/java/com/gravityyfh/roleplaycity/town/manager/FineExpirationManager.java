package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Gestionnaire automatique des expirations d'amendes et contestations
 * Vérifie périodiquement les amendes expirées et applique les actions appropriées
 */
public class FineExpirationManager {

    private final RoleplayCity plugin;
    private final TownPoliceManager policeManager;
    private final TownManager townManager;

    // Délais d'expiration (en jours)
    private static final int FINE_EXPIRATION_DAYS = 3;
    private static final int CONTESTATION_EXPIRATION_DAYS = 7;

    public FineExpirationManager(RoleplayCity plugin, TownPoliceManager policeManager, TownManager townManager) {
        this.plugin = plugin;
        this.policeManager = policeManager;
        this.townManager = townManager;
    }

    /**
     * Démarre la vérification automatique périodique
     * S'exécute toutes les heures (72000 ticks)
     */
    public void startAutomaticChecks() {
        // Vérifier toutes les heures (72000 ticks = 1 heure)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            plugin.getLogger().info("[FineExpiration] Vérification des amendes expirées...");

            int expiredFines = checkExpiredFines();
            int expiredContestations = checkExpiredContestations();

            plugin.getLogger().info(String.format("[FineExpiration] Amendes payées automatiquement: %d | Contestations annulées: %d",
                expiredFines, expiredContestations));
        }, 100L, 72000L); // Démarre après 5 secondes, puis toutes les heures
    }

    /**
     * Vérifie et traite les amendes PENDING expirées (3 jours)
     * @return Nombre d'amendes traitées
     */
    private int checkExpiredFines() {
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expirationThreshold = now.minusDays(FINE_EXPIRATION_DAYS);

        // Parcourir toutes les amendes
        for (List<Fine> townFines : policeManager.getFinesForSave().values()) {
            for (Fine fine : townFines) {
                // Vérifier si l'amende est PENDING et expirée
                if (fine.getStatus() == Fine.FineStatus.PENDING &&
                    fine.getIssueDate().isBefore(expirationThreshold)) {

                    // Paiement forcé
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        forceFinePayment(fine);
                    });
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Vérifie et traite les contestations CONTESTED expirées (7 jours)
     * @return Nombre de contestations traitées
     */
    private int checkExpiredContestations() {
        int count = 0;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expirationThreshold = now.minusDays(CONTESTATION_EXPIRATION_DAYS);

        // Parcourir toutes les amendes
        for (List<Fine> townFines : policeManager.getFinesForSave().values()) {
            for (Fine fine : townFines) {
                // Vérifier si la contestation est CONTESTED et expirée
                if (fine.getStatus() == Fine.FineStatus.CONTESTED &&
                    fine.getContestedDate() != null &&
                    fine.getContestedDate().isBefore(expirationThreshold)) {

                    // Annulation automatique
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        autoAnnulContestation(fine);
                    });
                    count++;
                }
            }
        }

        return count;
    }

    /**
     * Force le paiement d'une amende expirée (même en négatif)
     */
    private void forceFinePayment(Fine fine) {
        // Récupérer les paramètres de commission
        double policeCommissionPercentage = plugin.getConfig().getDouble("town.commissions.police-commission-percentage", 50.0);

        // Calculer les montants
        double totalAmount = fine.getAmount();
        double policeCommission = totalAmount * (policeCommissionPercentage / 100.0);
        double townShare = totalAmount - policeCommission;

        // Prélever l'argent du contrevenant (MÊME EN NÉGATIF)
        OfflinePlayer offender = Bukkit.getOfflinePlayer(fine.getOffenderUuid());
        RoleplayCity.getEconomy().withdrawPlayer(offender, totalAmount);

        // Verser la commission au policier
        OfflinePlayer policier = Bukkit.getOfflinePlayer(fine.getPolicierUuid());
        RoleplayCity.getEconomy().depositPlayer(policier, policeCommission);

        // Verser la part à la ville
        Town town = townManager.getTown(fine.getTownName());
        if (town != null) {
            town.deposit(townShare);
        }

        // Marquer comme payée
        fine.markAsPaid();

        plugin.getLogger().info(String.format("[FineExpiration] Paiement forcé: %s - %.2f€ (Amende expirée après 3 jours)",
            fine.getOffenderName(), totalAmount));

        // Notifications
        plugin.getNotificationManager().sendNotification(
            fine.getOffenderUuid(),
            NotificationManager.NotificationType.WARNING,
            "⚠ AMENDE PAYÉE AUTOMATIQUEMENT",
            String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§c§lPaiement forcé - Expiration\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Amende non payée dans les 3 jours\n§7Montant prélevé: §6-%.2f€\n§7Ville: §f%s\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§c⚠ Paiement automatique appliqué",
                totalAmount, fine.getTownName())
        );

        plugin.getNotificationManager().sendNotification(
            fine.getPolicierUuid(),
            NotificationManager.NotificationType.ECONOMY,
            "💰 COMMISSION (PAIEMENT AUTO)",
            String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a§lCommission reçue\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Contrevenant: §e%s\n§7Amende expirée et payée automatiquement\n§7Commission: §6+%.2f€\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                fine.getOffenderName(), policeCommission)
        );

        // Sauvegarder
        plugin.getTownFinesDataManager().saveFines(policeManager.getFinesForSave());
        townManager.saveTownsNow();
    }

    /**
     * Annule automatiquement une contestation expirée
     */
    private void autoAnnulContestation(Fine fine) {
        // Marquer comme annulée par jugement invalide
        fine.setStatus(Fine.FineStatus.JUDGED_INVALID);

        plugin.getLogger().info(String.format("[FineExpiration] Contestation annulée automatiquement: %s - %.2f€ (Non traitée après 7 jours)",
            fine.getOffenderName(), fine.getAmount()));

        // Notification au contrevenant
        plugin.getNotificationManager().sendNotification(
            fine.getOffenderUuid(),
            NotificationManager.NotificationType.INFO,
            "⚖ CONTESTATION ACCEPTÉE (AUTO)",
            String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a§lAmende annulée automatiquement\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Contestation non traitée dans les 7 jours\n§7Amende: §6%.2f€\n§7Ville: §f%s\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a✔ Vous ne devez rien payer",
                fine.getAmount(), fine.getTownName())
        );

        // Notification au policier
        plugin.getNotificationManager().sendNotification(
            fine.getPolicierUuid(),
            NotificationManager.NotificationType.WARNING,
            "⚖ CONTESTATION EXPIRÉE",
            String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§e§lAmende annulée - Expiration\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Contrevenant: §e%s\n§7Amende: §6%.2f€\n§7Contestation non traitée pendant 7 jours\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§e⚠ Annulation automatique",
                fine.getOffenderName(), fine.getAmount())
        );

        // Notifier TOUS les juges de la ville
        Town town = townManager.getTown(fine.getTownName());
        if (town != null) {
            for (UUID memberUuid : town.getMembers().keySet()) {
                TownRole role = town.getMemberRole(memberUuid);
                if (role == TownRole.JUGE || role == TownRole.MAIRE) {
                    plugin.getNotificationManager().sendNotification(
                        memberUuid,
                        NotificationManager.NotificationType.IMPORTANT,
                        "⚖ CONTESTATION NON TRAITÉE",
                        String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§c§lContestation expirée\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Contrevenant: §e%s\n§7Amende: §6%.2f€\n§7Motif: §f%s\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§c⚠ Aucun juge n'a traité ce dossier dans les 7 jours\n§e→ Annulation automatique appliquée",
                            fine.getOffenderName(), fine.getAmount(), fine.getReason())
                    );
                }
            }
        }

        // Sauvegarder
        plugin.getTownFinesDataManager().saveFines(policeManager.getFinesForSave());
    }

    /**
     * Vérification manuelle (utile pour debug ou commandes admin)
     */
    public void checkNow() {
        plugin.getLogger().info("[FineExpiration] Vérification manuelle lancée...");
        int expiredFines = checkExpiredFines();
        int expiredContestations = checkExpiredContestations();
        plugin.getLogger().info(String.format("[FineExpiration] Résultat: %d amendes payées | %d contestations annulées",
            expiredFines, expiredContestations));
    }
}
