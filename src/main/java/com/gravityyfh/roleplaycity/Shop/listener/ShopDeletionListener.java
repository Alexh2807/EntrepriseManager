package com.gravityyfh.roleplaycity.shop.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.shop.manager.ShopManager;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import com.gravityyfh.roleplaycity.town.event.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Listener central pour la suppression automatique des shops
 * basé sur les événements du système de ville
 */
public class ShopDeletionListener implements Listener {
    private final RoleplayCity plugin;
    private final ShopManager shopManager;

    public ShopDeletionListener(RoleplayCity plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    /**
     * Vérifie si une option de suppression est activée
     */
    private boolean isEnabled(String option) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("shop-deletion-on-events");
        if (config == null) {
            return true; // Par défaut, tout est activé
        }
        return config.getBoolean(option, true);
    }

    // ===== ÉVÉNEMENTS DE MEMBRE =====

    /**
     * Quand un membre quitte ou est expulsé d'une ville
     * → Supprimer ses shops s'il quitte la ville où est son entreprise
     */
    @EventHandler
    public void onMemberLeaveTown(TownMemberLeaveEvent event) {
        if (!isEnabled("resident-leave-town")) {
        }

        // La logique est déjà gérée dans TownEventListener qui supprime l'entreprise
        // Les shops seront supprimés via la suppression d'entreprise
        // Pas besoin de dupliquer la logique ici
    }

    /**
     * Quand un membre est expulsé (kick) d'une ville
     */
    @EventHandler
    public void onMemberKick(TownMemberKickEvent event) {
        if (!isEnabled("resident-kick-from-town")) {
        }

        // Idem que pour le leave - géré par la suppression d'entreprise
    }

    // ===== ÉVÉNEMENTS DE TERRAIN =====

    /**
     * Quand le propriétaire d'un terrain change
     * → Supprimer les shops de l'ancien propriétaire sur ce terrain
     */
    @EventHandler
    public void onPlotOwnerChange(PlotOwnerChangeEvent event) {
        if (!isEnabled("plot-owner-change")) {
            return;
        }

        Plot plot = event.getPlot();
        if (plot == null || event.getOldOwnerUuid() == null) {
            return;
        }

        // Supprimer les shops de l'ancien propriétaire sur ce terrain
        int deleted = shopManager.deleteShopsByOwnerOnPlot(
            event.getOldOwnerUuid(),
            plot,
            "Changement de propriétaire du terrain"
        );

        if (deleted > 0) {
            plugin.getLogger().info(String.format(
                "[ShopDeletion] %d shop(s) supprimé(s) sur le terrain %s:%d,%d (changement propriétaire)",
                deleted, plot.getWorldName(), plot.getChunkX(), plot.getChunkZ()
            ));
        }
    }

    /**
     * Quand un terrain est nettoyé (clearOwner)
     * → Supprimer tous les shops sur ce terrain
     */
    @EventHandler
    public void onPlotClear(PlotClearEvent event) {
        if (!isEnabled("plot-clear")) {
            return;
        }

        Plot plot = event.getPlot();
        if (plot == null) {
            return;
        }

        int deleted = shopManager.deleteShopsOnPlot(
            plot,
            "Terrain nettoyé: " + event.getReason()
        );

        if (deleted > 0) {
            plugin.getLogger().info(String.format(
                "[ShopDeletion] %d shop(s) supprimé(s) sur le terrain %s:%d,%d (terrain nettoyé)",
                deleted, plot.getWorldName(), plot.getChunkX(), plot.getChunkZ()
            ));
        }
    }

    /**
     * Quand le type d'un terrain change
     * → Si le terrain n'est plus PROFESSIONNEL, supprimer tous les shops
     */
    @EventHandler
    public void onPlotTypeChange(PlotTypeChangeEvent event) {
        if (!isEnabled("plot-type-change")) {
            return;
        }

        Plot plot = event.getPlot();
        PlotType newType = event.getNewType();

        // Si le nouveau type n'est PAS PROFESSIONNEL, supprimer les shops
        if (newType != PlotType.PROFESSIONNEL) {
            int deleted = shopManager.deleteShopsOnPlot(
                plot,
                "Terrain changé de PROFESSIONNEL vers " + newType.getDisplayName()
            );

            if (deleted > 0) {
                plugin.getLogger().info(String.format(
                    "[ShopDeletion] %d shop(s) supprimé(s) sur le terrain %s:%d,%d (type changé vers %s)",
                    deleted, plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), newType.getDisplayName()
                ));
            }
        }
    }

    /**
     * Quand un terrain est transféré à la ville
     * → Supprimer tous les shops de l'ancien propriétaire/entreprise
     */
    @EventHandler
    public void onPlotTransferToTown(PlotTransferToTownEvent event) {
        if (!isEnabled("plot-clear")) { // Utilise la même option que plot-clear
            return;
        }

        Plot plot = event.getPlot();
        if (plot == null) {
            return;
        }

        int deleted = shopManager.deleteShopsOnPlot(
            plot,
            "Terrain transféré à la ville: " + event.getReason()
        );

        if (deleted > 0) {
            plugin.getLogger().info(String.format(
                "[ShopDeletion] %d shop(s) supprimé(s) sur le terrain %s:%d,%d (transfert à la ville)",
                deleted, plot.getWorldName(), plot.getChunkX(), plot.getChunkZ()
            ));
        }
    }

    /**
     * Quand un terrain est unclaimed par la ville
     * → Supprimer tous les shops sur ce terrain
     */
    @EventHandler
    public void onTownUnclaimPlot(TownUnclaimPlotEvent event) {
        if (!isEnabled("town-unclaim-plot")) {
            return;
        }

        Plot plot = event.getPlot();
        if (plot == null) {
            return;
        }

        int deleted = shopManager.deleteShopsOnPlot(
            plot,
            "Terrain unclaimed par la ville " + event.getTownName()
        );

        if (deleted > 0) {
            plugin.getLogger().info(String.format(
                "[ShopDeletion] %d shop(s) supprimé(s) sur le terrain %s:%d,%d (unclaim)",
                deleted, plot.getWorldName(), plot.getChunkX(), plot.getChunkZ()
            ));
        }
    }

    /**
     * Quand une ville est supprimée
     * → Supprimer tous les shops de cette ville
     */
    @EventHandler
    public void onTownDelete(TownDeleteEvent event) {
        if (!isEnabled("town-ruin")) { // Utilise town-ruin pour correspondre à Towny
            return;
        }

        String townName = event.getTownName();
        int deleted = shopManager.deleteShopsInTown(
            townName,
            "Ville supprimée"
        );

        if (deleted > 0) {
            plugin.getLogger().info(String.format(
                "[ShopDeletion] %d shop(s) supprimé(s) dans la ville %s (ville supprimée)",
                deleted, townName
            ));
        }
    }
}
