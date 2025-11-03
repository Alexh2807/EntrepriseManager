package com.gravityyfh.roleplaycity.town.task;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.manager.TownEconomyManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Tâche récurrente pour gérer l'économie des villes
 * - Collecte automatique des taxes
 * - Vérification des locations expirées
 * - Nettoyage des invitations expirées
 */
public class TownEconomyTask extends BukkitRunnable {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownEconomyManager economyManager;

    private int tickCounter = 0;

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

        // Toutes les heures (72000 ticks) : Collecter les taxes automatiquement
        if (tickCounter % 72000 == 0) {
            economyManager.collectAllTaxes();
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
                town.getPlotGroups().values().forEach(group -> {
                    if (group.getRenterUuid() != null) {
                        group.updateRentDays();

                        // Synchroniser avec les parcelles individuelles
                        for (String plotKey : group.getPlotKeys()) {
                            String[] parts = plotKey.split(":");
                            if (parts.length == 3) {
                                String worldName = parts[0];
                                int chunkX = Integer.parseInt(parts[1]);
                                int chunkZ = Integer.parseInt(parts[2]);

                                var plot = town.getPlot(worldName, chunkX, chunkZ);
                                if (plot != null && plot.getRenterUuid() != null) {
                                    // Synchroniser le solde
                                    plot.setRentDaysRemaining(group.getRentDaysRemaining());
                                }
                            }
                        }
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
    }
}
