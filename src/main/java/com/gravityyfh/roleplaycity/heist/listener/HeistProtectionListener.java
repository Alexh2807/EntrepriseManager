package com.gravityyfh.roleplaycity.heist.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.heist.data.Heist;
import com.gravityyfh.roleplaycity.heist.data.HeistParticipant;
import com.gravityyfh.roleplaycity.heist.data.HeistPhase;
import com.gravityyfh.roleplaycity.heist.manager.HeistManager;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;

import java.util.UUID;

/**
 * Listener qui gère l'accès temporaire au terrain pendant un cambriolage.
 * Permet aux participants d'interagir avec le terrain pendant la phase de vol.
 *
 * Ce listener a une priorité LOWEST pour s'exécuter AVANT TownProtectionListener
 * et permettre le bypass des protections pour les cambrioleurs.
 */
public class HeistProtectionListener implements Listener {

    private final RoleplayCity plugin;
    private final HeistManager heistManager;
    private final TownManager townManager;

    public HeistProtectionListener(RoleplayCity plugin, HeistManager heistManager) {
        this.plugin = plugin;
        this.heistManager = heistManager;
        this.townManager = plugin.getTownManager();
    }

    /**
     * Vérifie si un joueur peut bypass les protections du terrain à cause d'un heist
     *
     * @return true si le joueur est un participant actif pendant la phase ROBBERY
     */
    public boolean canBypassProtection(Player player, Location location) {
        Heist heist = heistManager.getActiveHeistAt(location);
        if (heist == null) return false;

        // Seulement pendant la phase de vol
        if (heist.getPhase() != HeistPhase.ROBBERY) return false;

        // Vérifier si le joueur est un participant actif
        HeistParticipant participant = heist.getParticipant(player.getUniqueId());
        if (participant == null) return false;

        return participant.canAct();
    }

    /**
     * Permet aux cambrioleurs de casser des blocs pendant le vol
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        if (canBypassProtection(player, location)) {
            // Marquer l'événement comme "heist bypass" pour que TownProtectionListener le sache
            event.getBlock().setMetadata("heist_bypass",
                new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        }
    }

    /**
     * Permet aux cambrioleurs de placer des blocs (dynamite, etc.) pendant le vol
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();

        if (canBypassProtection(player, location)) {
            event.getBlock().setMetadata("heist_bypass",
                new org.bukkit.metadata.FixedMetadataValue(plugin, true));
        }
    }

    /**
     * Permet aux cambrioleurs d'interagir (ouvrir coffres, etc.) pendant le vol
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        Location location = event.getClickedBlock().getLocation();

        if (canBypassProtection(player, location)) {
            // Marquer le bloc pour bypass
            event.getClickedBlock().setMetadata("heist_bypass",
                new org.bukkit.metadata.FixedMetadataValue(plugin, true));

            // Enregistrer l'ouverture de coffre si applicable
            Heist heist = heistManager.getActiveHeistAt(location);
            if (heist != null && isContainer(event.getClickedBlock().getType())) {
                heist.recordChestOpened(player.getUniqueId());
            }
        }
    }

    /**
     * Gère la déconnexion d'un participant
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Vérifier si le joueur participe à un heist
        Heist heist = heistManager.getHeistForParticipant(playerId);
        if (heist == null) return;

        HeistParticipant participant = heist.getParticipant(playerId);
        if (participant != null && participant.canAct()) {
            participant.setDisconnected();
            plugin.getLogger().info("[Heist] Participant " + player.getName()
                + " s'est déconnecté pendant le cambriolage");
        }
    }

    /**
     * Gère la reconnexion d'un participant
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Vérifier si le joueur participait à un heist
        Heist heist = heistManager.getHeistForParticipant(playerId);
        if (heist == null) return;

        HeistParticipant participant = heist.getParticipant(playerId);
        if (participant == null) return;

        // Vérifier si la grâce n'est pas expirée
        if (participant.getStatus() == HeistParticipant.ParticipantStatus.DISCONNECTED) {
            if (!participant.isDisconnectGraceExpired(heistManager.getConfig().getDisconnectGraceMinutes())) {
                participant.setReconnected();
                player.sendMessage(ChatColor.GREEN + "Vous avez rejoint le cambriolage en cours!");
                plugin.getLogger().info("[Heist] Participant " + player.getName()
                    + " s'est reconnecté pendant le cambriolage");
            } else {
                player.sendMessage(ChatColor.RED + "Vous avez été absent trop longtemps. "
                    + "Vous n'êtes plus participant du cambriolage.");
            }
        }

        // Notifier le propriétaire s'il se connecte
        if (heistManager.getConfig().shouldNotifyOwnerOnJoin()) {
            notifyOwnerIfApplicable(player, heist);
        }
    }

    /**
     * Notifie le propriétaire du terrain s'il se connecte pendant le cambriolage
     */
    private void notifyOwnerIfApplicable(Player player, Heist heist) {
        Town town = townManager.getTown(heist.getTownName());
        if (town == null) return;

        String plotKey = heist.getPlotKey();
        String[] parts = plotKey.split(":");
        if (parts.length != 3) return;

        try {
            int chunkX = Integer.parseInt(parts[1]);
            int chunkZ = Integer.parseInt(parts[2]);
            var plot = town.getPlot(parts[0], chunkX, chunkZ);

            if (plot != null && plot.getOwnerUuid() != null
                && plot.getOwnerUuid().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD
                    + "⚠ ALERTE: Votre terrain est en train d'être cambriolé!");
                player.sendMessage(ChatColor.YELLOW + "Rendez-vous sur place pour défendre vos biens!");
            }
        } catch (NumberFormatException ignored) {}
    }

    /**
     * Gère la mort d'un participant pendant le heist
     */
    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID playerId = player.getUniqueId();

        Heist heist = heistManager.getHeistForParticipant(playerId);
        if (heist == null) return;

        HeistParticipant participant = heist.getParticipant(playerId);
        if (participant != null && participant.canAct()) {
            heist.recordDeath(playerId);
            player.sendMessage(ChatColor.RED + "Vous êtes mort pendant le cambriolage!");

            // Vérifier si tous les participants sont hors-jeu
            heistManager.checkAllParticipantsOut(heist);
        }
    }

    /**
     * Vérifie si un type de bloc est un conteneur
     */
    private boolean isContainer(org.bukkit.Material material) {
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL, SHULKER_BOX,
                 WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX,
                 PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX,
                 CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX,
                 BLACK_SHULKER_BOX, ENDER_CHEST,
                 FURNACE, BLAST_FURNACE, SMOKER,
                 DISPENSER, DROPPER, HOPPER, BREWING_STAND -> true;
            default -> false;
        };
    }
}
