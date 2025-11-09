package com.gravityyfh.roleplaycity.town.manager;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Fine;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Gestionnaire du système de police des villes
 * Gère les amendes et les infractions
 */
public class TownPoliceManager {

    private final RoleplayCity plugin;
    private final TownManager townManager;

    // Amendes par ville
    private final Map<String, List<Fine>> townFines;

    // Index pour recherche rapide: joueur -> amendes
    private final Map<UUID, List<Fine>> playerFines;

    public TownPoliceManager(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.townFines = new ConcurrentHashMap<>();
        this.playerFines = new ConcurrentHashMap<>();
    }

    /**
     * Émettre une amende
     */
    public Fine issueFine(String townName, UUID offenderUuid, String offenderName,
                         Player policier, String reason, double amount) {
        Town town = townManager.getTown(townName);
        if (town == null) {
            return null;
        }

        // Vérifier que le policier a le rôle approprié
        TownRole role = town.getMemberRole(policier.getUniqueId());
        if (role != TownRole.POLICIER && role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            return null;
        }

        // Vérifier qu'il y a au moins un juge ou le maire dans la ville
        boolean hasJudge = town.getMembers().entrySet().stream()
            .anyMatch(entry -> {
                TownRole memberRole = entry.getValue().getRole();
                return memberRole == TownRole.JUGE || memberRole == TownRole.MAIRE;
            });

        if (!hasJudge) {
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            policier.sendMessage("§c✖ §lÉMISSION IMPOSSIBLE");
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            policier.sendMessage("§7Aucun juge disponible dans la ville");
            policier.sendMessage("§7pour traiter les éventuelles contestations");
            policier.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return null;
        }

        // Créer l'amende
        Fine fine = new Fine(
            townName,
            offenderUuid,
            offenderName,
            policier.getUniqueId(),
            policier.getName(),
            reason,
            amount
        );

        // Ajouter aux index
        townFines.computeIfAbsent(townName, k -> new ArrayList<>()).add(fine);
        playerFines.computeIfAbsent(offenderUuid, k -> new ArrayList<>()).add(fine);

        // Envoyer notification au contrevenant
        plugin.getNotificationManager().sendNotification(
            offenderUuid,
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.WARNING,
            "⚠ AMENDE REÇUE",
            String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§c§lAmende émise\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Ville: §f%s\n§7Montant: §6%.2f€\n§7Motif: §f%s\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§e⚡ Payez ou contestez via §f/ville §e→ §fMes Amendes",
                townName, amount, reason)
        );

        plugin.getLogger().info("Amende émise dans " + townName + ": " + offenderName +
            " - " + amount + "€ (" + reason + ")");

        // Sauvegarder immédiatement
        plugin.getTownFinesDataManager().saveFines(getFinesForSave());

        return fine;
    }

    /**
     * Payer une amende
     */
    public boolean payFine(Fine fine, Player player) {
        if (!fine.isPending()) {
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c✖ Cette amende ne peut plus être payée");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Vérifier que le joueur a assez d'argent
        if (!RoleplayCity.getEconomy().has(player, fine.getAmount())) {
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c✖ §fFonds insuffisants");
            player.sendMessage("§7Montant requis: §6" + String.format("%.2f€", fine.getAmount()));
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        // Récupérer le pourcentage de commission du policier depuis la config
        double policeCommissionPercentage = plugin.getConfig().getDouble("town.commissions.police-commission-percentage", 50.0);

        // Calculer les montants
        double totalAmount = fine.getAmount();
        double policeCommission = totalAmount * (policeCommissionPercentage / 100.0);
        double townShare = totalAmount - policeCommission;

        // Prélever l'argent du contrevenant
        RoleplayCity.getEconomy().withdrawPlayer(player, totalAmount);

        // Verser la commission au policier
        Player policier = Bukkit.getPlayer(fine.getPolicierUuid());
        if (policier != null && policier.isOnline()) {
            RoleplayCity.getEconomy().depositPlayer(policier, policeCommission);

            // Notification au policier
            plugin.getNotificationManager().sendNotification(
                fine.getPolicierUuid(),
                com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.ECONOMY,
                "💰 COMMISSION POLICIER",
                String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a§lCommission reçue\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Contrevenant: §e%s\n§7Commission: §6+%.2f€\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a✔ Amende payée avec succès",
                    fine.getOffenderName(), policeCommission)
            );
        } else {
            // Si le policier est hors ligne, lui donner quand même l'argent
            RoleplayCity.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(fine.getPolicierUuid()), policeCommission);
        }

        // Verser la part à la ville
        Town town = townManager.getTown(fine.getTownName());
        if (town != null) {
            town.deposit(townShare);
        }

        // Marquer comme payée
        fine.markAsPaid();

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§a✔ §lAMENDE PAYÉE");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§7Montant total: §6-" + String.format("%.2f€", totalAmount));
        player.sendMessage("§7Ville: §f" + fine.getTownName());
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        plugin.getLogger().info(String.format("Amende payée: %s - %.2f€ (Policier: %.2f€, Ville: %.2f€)",
            player.getName(), totalAmount, policeCommission, townShare));

        // Notification économique au contrevenant
        plugin.getNotificationManager().sendNotification(
            player.getUniqueId(),
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.ECONOMY,
            "💳 PAIEMENT EFFECTUÉ",
            String.format(" §8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a§lAmende payée\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Ville: §f%s\n§7Montant: §6-%.2f€\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§a✔ Paiement traité avec succès",
                fine.getTownName(), totalAmount)
        );

        // Sauvegarder immédiatement (amendes + banque ville)
        plugin.getTownFinesDataManager().saveFines(getFinesForSave());
        townManager.saveTownsNow();

        return true;
    }

    /**
     * Contester une amende
     */
    public boolean contestFine(Fine fine, Player player, String contestReason) {
        if (!fine.canBeContested()) {
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c✖ Contestation impossible");
            player.sendMessage("§7Cette amende ne peut plus être contestée");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            return false;
        }

        fine.markAsContested(contestReason);

        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§e⚖ §lCONTESTATION ENREGISTRÉE");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        player.sendMessage("§7Votre dossier sera examiné par un juge");
        player.sendMessage("§7Vous serez notifié du verdict");
        player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");

        // Notification au contestataire
        plugin.getNotificationManager().sendNotification(
            player.getUniqueId(),
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
            "⚖ CONTESTATION ENREGISTRÉE",
            String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§e§lContestation d'amende\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Ville: §f%s\n§7Montant: §6%.2f€\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§e⚖ En attente de jugement",
                fine.getTownName(), fine.getAmount())
        );

        // Notifier les juges et le maire
        Town town = townManager.getTown(fine.getTownName());
        if (town != null) {
            for (UUID memberUuid : town.getMembers().keySet()) {
                TownRole role = town.getMemberRole(memberUuid);
                if (role == TownRole.JUGE || role == TownRole.MAIRE) {
                    plugin.getNotificationManager().sendNotification(
                        memberUuid,
                        com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.IMPORTANT,
                        "⚖ NOUVELLE CONTESTATION",
                        String.format("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§5§lAmende contestée\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§7Contrevenant: §e%s\n§7Montant: §6%.2f€\n§7Motif: §f%s\n§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n§d⚖ Action requise: Jugement",
                            player.getName(), fine.getAmount(), fine.getReason())
                    );
                }
            }
        }

        plugin.getLogger().info("Amende contestée: " + player.getName() + " - Raison: " + contestReason);

        // Sauvegarder immédiatement
        plugin.getTownFinesDataManager().saveFines(getFinesForSave());

        return true;
    }

    /**
     * Annuler une amende (policier/maire uniquement)
     */
    public boolean cancelFine(Fine fine, Player canceller) {
        Town town = townManager.getTown(fine.getTownName());
        if (town == null) {
            return false;
        }

        TownRole role = town.getMemberRole(canceller.getUniqueId());
        if (role != TownRole.POLICIER && role != TownRole.MAIRE && role != TownRole.ADJOINT) {
            return false;
        }

        fine.cancel();

        // Notifier le contrevenant
        Player offender = Bukkit.getPlayer(fine.getOffenderUuid());
        if (offender != null && offender.isOnline()) {
            offender.sendMessage(ChatColor.GREEN + "L'amende de " + fine.getAmount() +
                "€ a été annulée par " + canceller.getName());
        }

        plugin.getLogger().info("Amende annulée par " + canceller.getName() + ": " + fine);

        // Sauvegarder immédiatement
        plugin.getTownFinesDataManager().saveFines(getFinesForSave());

        return true;
    }

    /**
     * Récupérer toutes les amendes d'un joueur
     */
    public List<Fine> getPlayerFines(UUID playerUuid) {
        return playerFines.getOrDefault(playerUuid, new ArrayList<>());
    }

    /**
     * Récupérer les amendes non payées d'un joueur
     */
    public List<Fine> getUnpaidFines(UUID playerUuid) {
        return getPlayerFines(playerUuid).stream()
            .filter(Fine::isPending)
            .collect(Collectors.toList());
    }

    /**
     * Vérifie si un joueur a des amendes impayées
     */
    public boolean hasUnpaidFines(UUID playerUuid) {
        return !getUnpaidFines(playerUuid).isEmpty();
    }

    /**
     * Récupérer toutes les amendes d'une ville
     */
    public List<Fine> getTownFines(String townName) {
        return townFines.getOrDefault(townName, new ArrayList<>());
    }

    /**
     * Récupérer les amendes contestées d'une ville
     */
    public List<Fine> getContestedFines(String townName) {
        return getTownFines(townName).stream()
            .filter(Fine::isContested)
            .collect(Collectors.toList());
    }

    /**
     * Calculer le total des amendes impayées d'un joueur
     */
    public double getTotalUnpaidFines(UUID playerUuid) {
        return getUnpaidFines(playerUuid).stream()
            .mapToDouble(Fine::getAmount)
            .sum();
    }

    /**
     * Récupérer une amende par son ID
     */
    public Fine getFineById(UUID fineId) {
        for (List<Fine> fines : townFines.values()) {
            for (Fine fine : fines) {
                if (fine.getFineId().equals(fineId)) {
                    return fine;
                }
            }
        }
        return null;
    }

    /**
     * Charger les amendes depuis les données
     */
    public void loadFines(Map<String, List<Fine>> loadedFines) {
        townFines.clear();
        playerFines.clear();
        townFines.putAll(loadedFines);

        // Reconstruire l'index playerFines
        for (List<Fine> fines : townFines.values()) {
            for (Fine fine : fines) {
                playerFines.computeIfAbsent(fine.getOffenderUuid(), k -> new ArrayList<>()).add(fine);
            }
        }

        int totalFines = townFines.values().stream().mapToInt(List::size).sum();
        plugin.getLogger().info("Chargé " + totalFines + " amendes.");
    }

    /**
     * Récupérer les amendes pour sauvegarde
     */
    public Map<String, List<Fine>> getFinesForSave() {
        return new HashMap<>(townFines);
    }

    /**
     * Statistiques des amendes d'une ville
     */
    public FineStatistics getTownStatistics(String townName) {
        List<Fine> fines = getTownFines(townName);

        long totalFines = fines.size();
        long paidFines = fines.stream().filter(f -> f.getStatus() == Fine.FineStatus.PAID).count();
        long contestedFines = fines.stream().filter(Fine::isContested).count();
        long pendingFines = fines.stream().filter(Fine::isPending).count();

        double totalAmount = fines.stream().mapToDouble(Fine::getAmount).sum();
        double collectedAmount = fines.stream()
            .filter(f -> f.getStatus() == Fine.FineStatus.PAID)
            .mapToDouble(Fine::getAmount)
            .sum();

        return new FineStatistics(totalFines, paidFines, contestedFines, pendingFines,
            totalAmount, collectedAmount);
    }

    public static class FineStatistics {
        public final long totalFines;
        public final long paidFines;
        public final long contestedFines;
        public final long pendingFines;
        public final double totalAmount;
        public final double collectedAmount;

        public FineStatistics(long totalFines, long paidFines, long contestedFines,
                            long pendingFines, double totalAmount, double collectedAmount) {
            this.totalFines = totalFines;
            this.paidFines = paidFines;
            this.contestedFines = contestedFines;
            this.pendingFines = pendingFines;
            this.totalAmount = totalAmount;
            this.collectedAmount = collectedAmount;
        }
    }
}
