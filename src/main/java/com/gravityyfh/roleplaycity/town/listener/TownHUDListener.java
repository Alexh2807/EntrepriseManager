package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener pour afficher un HUD quand un joueur entre dans un territoire
 */
public class TownHUDListener implements Listener {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    // Mémoriser le dernier état de territoire de chaque joueur
    private final Map<UUID, TerritoryState> lastTerritoryState = new HashMap<>();

    public TownHUDListener(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
    }

    /**
     * Classe pour représenter l'état d'un territoire
     */
    private static class TerritoryState {
        final String townName; // null si zone sauvage
        final String plotInfo; // Info complète sur la parcelle

        TerritoryState(String townName, String plotInfo) {
            this.townName = townName;
            this.plotInfo = plotInfo;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TerritoryState other)) return false;
            if (townName == null && other.townName == null) return true; // Deux zones sauvages
            if (townName == null || other.townName == null) return false; // Une ville et une zone sauvage
            return townName.equals(other.townName) && plotInfo.equals(other.plotInfo);
        }

        @Override
        public int hashCode() {
            return (townName != null ? townName.hashCode() : 0) * 31 + (plotInfo != null ? plotInfo.hashCode() : 0);
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Chunk fromChunk = event.getFrom().getChunk();
        Chunk toChunk = event.getTo().getChunk();

        // Vérifier si le joueur a changé de chunk
        if (fromChunk.getX() == toChunk.getX() && fromChunk.getZ() == toChunk.getZ()) {
            return; // Même chunk, pas besoin de mettre à jour
        }

        // Vérifier si ce chunk est claim
        String claimingTown = claimManager.getClaimOwner(toChunk);

        TerritoryState newState;
        String displayMessage;

        if (claimingTown != null) {
            // Chunk claim
            Town town = townManager.getTown(claimingTown);
            if (town == null) {
                return;
            }

            Plot plot = town.getPlot(toChunk.getWorld().getName(), toChunk.getX(), toChunk.getZ());
            if (plot == null) {
                return;
            }

            // Construire l'info de la parcelle pour comparaison
            StringBuilder plotInfo = new StringBuilder();
            plotInfo.append(plot.getType().name());
            if (plot.getOwnerUuid() != null) {
                plotInfo.append(":").append(plot.getOwnerName());
            }
            if (plot.isMunicipal() && plot.getMunicipalSubType() != null) {
                plotInfo.append(":").append(plot.getMunicipalSubType().name());
            }

            newState = new TerritoryState(claimingTown, plotInfo.toString());

            // Construire le message HUD
            StringBuilder hud = new StringBuilder();
            hud.append(ChatColor.GOLD).append("⚑ ");
            hud.append(ChatColor.AQUA).append(claimingTown);
            hud.append(ChatColor.GRAY).append(" | ");
            hud.append(plot.getType().getDisplayName());

            // Ajouter le propriétaire si c'est une parcelle privée
            if (plot.getOwnerUuid() != null) {
                String ownerName = plot.getOwnerName() != null ? plot.getOwnerName() : "Inconnu";
                hud.append(ChatColor.GRAY).append(" - ");
                hud.append(ChatColor.YELLOW).append(ownerName);
            }

            // Ajouter le sous-type si municipal
            if (plot.isMunicipal() && plot.getMunicipalSubType() != null) {
                hud.append(ChatColor.GRAY).append(" (");
                hud.append(plot.getMunicipalSubType().getDisplayName());
                hud.append(ChatColor.GRAY).append(")");
            }

            displayMessage = hud.toString();
        } else {
            // Chunk non claim (zone sauvage)
            newState = new TerritoryState(null, null);
            displayMessage = ChatColor.GREEN + "⚐ Zone Sauvage" + ChatColor.GRAY + " | Non revendiqué";
        }

        // Comparer avec l'état précédent
        TerritoryState lastState = lastTerritoryState.get(player.getUniqueId());

        // Afficher uniquement si l'état a changé
        if (lastState == null || !lastState.equals(newState)) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(displayMessage));
            lastTerritoryState.put(player.getUniqueId(), newState);
        }
    }

    /**
     * Nettoyer les données quand un joueur quitte
     */
    public void removePlayer(UUID playerUuid) {
        lastTerritoryState.remove(playerUuid);
    }
}
