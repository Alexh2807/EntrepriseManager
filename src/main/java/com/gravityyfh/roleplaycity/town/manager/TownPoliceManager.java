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
            "Amende reçue !",
            String.format("Vous avez reçu une amende de %.2f€ à %s pour: %s. Payez ou contestez dans /ville → Mes Amendes",
                amount, townName, reason)
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
            player.sendMessage(ChatColor.RED + "Cette amende ne peut plus être payée.");
            return false;
        }

        // Vérifier que le joueur a assez d'argent
        if (!RoleplayCity.getEconomy().has(player, fine.getAmount())) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent. Montant: " + fine.getAmount() + "€");
            return false;
        }

        // Prélever l'argent
        RoleplayCity.getEconomy().withdrawPlayer(player, fine.getAmount());

        // Verser à la ville
        Town town = townManager.getTown(fine.getTownName());
        if (town != null) {
            town.deposit(fine.getAmount());
        }

        // Marquer comme payée
        fine.markAsPaid();

        player.sendMessage(ChatColor.GREEN + "Amende payée avec succès: " + fine.getAmount() + "€");
        plugin.getLogger().info("Amende payée: " + player.getName() + " - " + fine.getAmount() + "€");

        // Notification économique
        plugin.getNotificationManager().sendNotification(
            player.getUniqueId(),
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.ECONOMY,
            "Amende payée",
            String.format("Vous avez payé une amende de %.2f€ à %s.", fine.getAmount(), fine.getTownName())
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
            player.sendMessage(ChatColor.RED + "Cette amende ne peut plus être contestée.");
            return false;
        }

        fine.markAsContested();

        player.sendMessage(ChatColor.YELLOW + "Votre contestation a été enregistrée.");
        player.sendMessage(ChatColor.GRAY + "Elle sera examinée par un juge.");

        // Notification au contestataire
        plugin.getNotificationManager().sendNotification(
            player.getUniqueId(),
            com.gravityyfh.roleplaycity.town.manager.NotificationManager.NotificationType.INFO,
            "Contestation enregistrée",
            String.format("Votre contestation d'amende de %.2f€ à %s a été enregistrée. Un juge l'examinera.",
                fine.getAmount(), fine.getTownName())
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
                        "Amende contestée",
                        String.format("%s a contesté une amende de %.2f€. Motif: %s",
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
