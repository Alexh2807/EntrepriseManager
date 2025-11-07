package com.gravityyfh.roleplaycity.town.task;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.manager.TownEconomyManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Tâche récurrente pour gérer l'économie des villes
 * - Collecte automatique des taxes (TOUTES LES HEURES - synchronisé avec paiements entreprises)
 * - Vérification des locations expirées
 * - Nettoyage des invitations expirées
 */
public class TownEconomyTask extends BukkitRunnable {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownEconomyManager economyManager;

    private int tickCounter = 0;
    private BukkitRunnable hourlyTaxTask; // Tâche horaire pour les taxes

    public TownEconomyTask(RoleplayCity plugin, TownManager townManager, TownEconomyManager economyManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.economyManager = economyManager;
    }

    @Override
    public void run() {
        tickCounter++;

        // Toutes les 5 minutes (6000 ticks) : Vérifier et mettre à jour les soldes de location
        if (tickCounter % 6000 == 0) {
            updateAllRentDays();
            updateAllGroupRentDays();
            economyManager.checkExpiredRents();
            economyManager.checkExpiredGroupRents();
        }

        // Toutes les 30 minutes (36000 ticks) : Nettoyer les invitations expirées
        if (tickCounter % 36000 == 0) {
            townManager.cleanupExpiredInvitations();
        }

        // Reset du compteur après 24h pour éviter l'overflow
        if (tickCounter >= 1728000) { // 24 heures en ticks
            tickCounter = 0;
        }
    }

    /**
     * Met à jour les soldes de location de toutes les parcelles
     */
    private void updateAllRentDays() {
        townManager.getTownNames().forEach(townName -> {
            var town = townManager.getTown(townName);
            if (town != null) {
                town.getPlots().values().forEach(plot -> {
                    if (plot.getRenterUuid() != null) {
                        plot.updateRentDays();
                    }
                });
            }
        });
    }

    /**
     * Met à jour les soldes de location de tous les groupes de parcelles
     */
    private void updateAllGroupRentDays() {
        townManager.getTownNames().forEach(townName -> {
            var town = townManager.getTown(townName);
            if (town != null) {
                // ⚠️ NOUVEAU SYSTÈME : PlotGroup autonome - pas de synchronisation avec plots individuels
                town.getPlotGroups().values().forEach(group -> {
                    if (group.getRenterUuid() != null) {
                        group.updateRentDays();
                        // Le groupe gère ses propres données de location de manière autonome
                        // Les plots individuels n'existent plus dans ce contexte
                    }
                });
            }
        });
    }

    /**
     * Démarre la tâche récurrente
     * S'exécute toutes les 20 ticks (1 seconde)
     */
    public void start() {
        this.runTaskTimer(plugin, 20L, 20L); // Delay 1 seconde, repeat toutes les secondes
        plugin.getLogger().info("TownEconomyTask démarrée (vérifications toutes les secondes)");

        // Démarrer la collecte des taxes horaire (synchronisée avec les paiements entreprises)
        startHourlyTaxCollection();
    }

    /**
     * Démarre la collecte horaire des taxes, synchronisée avec les paiements entreprises
     * S'exécute à chaque heure pile (14:00:00, 15:00:00, etc.)
     */
    private void startHourlyTaxCollection() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextFullHour = now.withMinute(0).withSecond(0).withNano(0).plusHours(1);
        long initialDelayTicks = java.time.Duration.between(now, nextFullHour).toSeconds() * 20L;

        // Sécurité pour éviter un délai négatif
        if (initialDelayTicks <= 0) {
            initialDelayTicks = 20L * 60L * 60L; // Reprogramme dans une heure
            nextFullHour = nextFullHour.plusHours(1);
        }

        long ticksParHeure = 20L * 60L * 60L; // 72000 ticks = 1 heure

        hourlyTaxTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Collecte horaire des taxes (au lieu de toutes les 24h)
                plugin.getLogger().info("Début de la collecte horaire des taxes...");
                economyManager.collectAllTaxesHourly();
                plugin.getLogger().info("Collecte horaire des taxes terminée.");
            }
        };

        hourlyTaxTask.runTaskTimer(plugin, initialDelayTicks, ticksParHeure);

        plugin.getLogger().info("Collecte horaire des taxes planifiée. Prochaine exécution vers: " +
            nextFullHour.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }

    /**
     * Arrête toutes les tâches
     */
    public void stop() {
        this.cancel();
        if (hourlyTaxTask != null) {
            hourlyTaxTask.cancel();
        }
    }
}
