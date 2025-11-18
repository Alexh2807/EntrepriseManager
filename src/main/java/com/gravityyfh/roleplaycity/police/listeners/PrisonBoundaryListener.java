package com.gravityyfh.roleplaycity.police.listeners;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.ImprisonedPlayerData;
import com.gravityyfh.roleplaycity.police.data.PrisonData;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Listener pour gérer les limites de zone des prisonniers
 * Empêche les prisonniers de sortir du claim COMMISSARIAT
 */
public class PrisonBoundaryListener implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ImprisonedPlayerData imprisonedData;

    public PrisonBoundaryListener(RoleplayCity plugin, TownManager townManager, ImprisonedPlayerData imprisonedData) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.imprisonedData = imprisonedData;
    }

    /**
     * Vérifie que le prisonnier reste dans le claim COMMISSARIAT
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onImprisonedPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!imprisonedData.isImprisoned(player)) {
            return;
        }

        PrisonData prisonData = imprisonedData.getPrisonData(player.getUniqueId());
        if (prisonData == null) {
            return;
        }

        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // Vérifier si le joueur est toujours dans le bon claim
        Town town = townManager.getTown(prisonData.getTownName());
        if (town == null) {
            // Ville supprimée, libérer le prisonnier
            // Note: Appeler via le manager au lieu du plugin directement
            return;
        }

        // Trouver le plot par son numéro
        Plot prisonPlot = null;
        for (Plot plot : town.getPlots().values()) {
            if (prisonData.getPlotIdentifier().equals(plot.getPlotNumber())) {
                prisonPlot = plot;
                break;
            }
        }

        if (prisonPlot == null) {
            // Plot supprimé, libérer le prisonnier
            return;
        }

        // Vérifier si le joueur est dans un chunk du plot
        Chunk currentChunk = to.getChunk();
        boolean isInPrisonPlot = prisonPlot.containsChunk(
            currentChunk.getWorld().getName(),
            currentChunk.getX(),
            currentChunk.getZ()
        );

        if (!isInPrisonPlot) {
            // Le joueur essaie de sortir du COMMISSARIAT
            Location prisonSpawn = prisonPlot.getPrisonSpawn();

            if (prisonSpawn != null) {
                // Téléporter au spawn de la prison
                player.teleport(prisonSpawn);
            } else {
                // Pas de spawn défini, téléporter au centre du premier chunk
                String firstChunkKey = prisonPlot.getChunks().get(0);
                String[] parts = firstChunkKey.split(":");

                if (parts.length >= 3) {
                    String worldName = parts[0];
                    int chunkX = Integer.parseInt(parts[1]);
                    int chunkZ = Integer.parseInt(parts[2]);

                    org.bukkit.World world = plugin.getServer().getWorld(worldName);
                    if (world != null) {
                        // Centre du chunk (8, 64, 8)
                        Location center = new Location(world, chunkX * 16 + 8, 64, chunkZ * 16 + 8);
                        // Trouver le sol
                        center = findSafeLocation(center);
                        player.teleport(center);
                    }
                }
            }

            // Message d'avertissement
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§c§l⛓️ LIMITE DE PRISON");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            player.sendMessage("§7Vous ne pouvez pas sortir du");
            player.sendMessage("§7COMMISSARIAT pendant votre peine");
            player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        }
    }

    /**
     * Empêche le respawn en dehors de la prison
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onImprisonedPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (!imprisonedData.isImprisoned(player)) {
            return;
        }

        PrisonData prisonData = imprisonedData.getPrisonData(player.getUniqueId());
        if (prisonData == null) {
            return;
        }

        // Forcer le respawn au spawn de la prison
        Town town = townManager.getTown(prisonData.getTownName());
        if (town != null) {
            // Trouver le plot par son numéro
            Plot prisonPlot = null;
            for (Plot plot : town.getPlots().values()) {
                if (prisonData.getPlotIdentifier().equals(plot.getPlotNumber())) {
                    prisonPlot = plot;
                    break;
                }
            }

            if (prisonPlot != null && prisonPlot.getPrisonSpawn() != null) {
                event.setRespawnLocation(prisonPlot.getPrisonSpawn());

                // Message
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    player.sendMessage("§c§l⛓️ RESPAWN EN PRISON");
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                    player.sendMessage("§7Vous êtes toujours emprisonné");
                    player.sendMessage("§7Temps restant: §c" + prisonData.getFormattedRemainingTime());
                    player.sendMessage("§8▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
                }, 10L); // Délai de 0.5 secondes pour laisser le respawn se faire
            }
        }
    }

    /**
     * Trouve un emplacement sûr pour téléporter un joueur
     */
    private Location findSafeLocation(Location location) {
        org.bukkit.World world = location.getWorld();
        if (world == null) {
            return location;
        }

        int x = location.getBlockX();
        int z = location.getBlockZ();

        // Chercher le bloc solide le plus haut
        int y = world.getHighestBlockYAt(x, z);

        // S'assurer qu'il y a 2 blocs d'air au-dessus
        while (y > 0 && !world.getBlockAt(x, y, z).getType().isSolid()) {
            y--;
        }

        // Placer le joueur sur le bloc solide
        return new Location(world, x + 0.5, y + 1, z + 0.5, location.getYaw(), location.getPitch());
    }
}
