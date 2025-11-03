package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
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
            if (plot.isForSale()) {
                plotInfo.append(":VENTE:").append(plot.getSalePrice());
            }
            if (plot.isForRent()) {
                plotInfo.append(":LOCATION:").append(plot.getRentPricePerDay());
            }
            if (plot.getRenterUuid() != null) {
                plotInfo.append(":LOUE:").append(plot.getRentDaysRemaining());
            }

            newState = new TerritoryState(claimingTown, plotInfo.toString());

            // Construire le message HUD (ActionBar)
            StringBuilder hud = new StringBuilder();
            hud.append(ChatColor.GOLD).append("⚑ ");
            hud.append(ChatColor.AQUA).append(claimingTown);
            hud.append(ChatColor.GRAY).append(" | ");

            // Afficher la surface uniquement pour les parcelles privées ou en vente/location
            if (plot.getOwnerUuid() != null || plot.isForSale() || plot.isForRent()) {
                hud.append(ChatColor.WHITE).append("256m²");
                hud.append(ChatColor.GRAY).append(" | ");
            }

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

            // Ajouter statut vente/location
            if (plot.isForSale()) {
                hud.append(ChatColor.GREEN).append(" [À VENDRE: ").append(String.format("%.0f€", plot.getSalePrice())).append("]");
            }
            if (plot.isForRent()) {
                hud.append(ChatColor.AQUA).append(" [LOCATION: ").append(String.format("%.0f€/j", plot.getRentPricePerDay())).append("]");
            }
            if (plot.getRenterUuid() != null) {
                hud.append(ChatColor.LIGHT_PURPLE).append(" [Loué: ").append(plot.getRentDaysRemaining()).append("j]");
            }

            displayMessage = hud.toString();

            // Envoyer message détaillé dans le chat si en vente ou location
            if (plot.isForSale() || plot.isForRent()) {
                sendDetailedPlotInfo(player, plot, claimingTown);
            }
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
     * Envoie un message détaillé dans le chat pour les parcelles en vente/location
     */
    private void sendDetailedPlotInfo(Player player, Plot plot, String townName) {
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");

        if (plot.isForSale()) {
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "🏠 PARCELLE À VENDRE");
        } else if (plot.isForRent()) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "🏠 PARCELLE EN LOCATION");
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");

        // Informations de base
        player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + plot.getType().getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + "256 m² " + ChatColor.GRAY + "(16x16)");
        player.sendMessage(ChatColor.YELLOW + "Position: " + ChatColor.WHITE + "X: " + (plot.getChunkX() * 16) + " Z: " + (plot.getChunkZ() * 16));

        // Propriétaire
        if (plot.getOwnerUuid() != null) {
            player.sendMessage(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + plot.getOwnerName());
        }

        player.sendMessage("");

        // Informations de vente
        if (plot.isForSale()) {
            player.sendMessage(ChatColor.GREEN + "💰 Prix de vente: " + ChatColor.GOLD + String.format("%.2f€", plot.getSalePrice()));
            player.sendMessage("");

            // Bouton cliquable pour acheter
            TextComponent buyButton = new TextComponent("  [💰 ACHETER CETTE PARCELLE]");
            buyButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            buyButton.setBold(true);
            buyButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/ville:buyplot " + plot.getChunkX() + " " + plot.getChunkZ() + " " + plot.getWorldName()));
            buyButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Cliquez pour acheter\nPrix: " + String.format("%.2f€", plot.getSalePrice()))
                    .color(net.md_5.bungee.api.ChatColor.YELLOW)
                    .create()
            ));
            player.spigot().sendMessage(buyButton);
        }

        // Informations de location
        if (plot.isForRent()) {
            player.sendMessage(ChatColor.AQUA + "📅 Prix de location: " + ChatColor.GOLD + String.format("%.2f€/jour", plot.getRentPricePerDay()));
            player.sendMessage(ChatColor.GRAY + "Solde maximum: 30 jours rechargeable");

            if (plot.getRenterUuid() != null) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Actuellement loué: " + plot.getRentDaysRemaining() + " jours restants");
            } else {
                player.sendMessage(ChatColor.GREEN + "Disponible immédiatement");
            }

            player.sendMessage("");

            // Bouton cliquable pour louer
            TextComponent rentButton = new TextComponent("  [📅 LOUER CETTE PARCELLE]");
            rentButton.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            rentButton.setBold(true);
            rentButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/ville:rentplot " + plot.getChunkX() + " " + plot.getChunkZ() + " " + plot.getWorldName()));
            rentButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Cliquez pour louer\nPrix: " + String.format("%.2f€/jour", plot.getRentPricePerDay()))
                    .color(net.md_5.bungee.api.ChatColor.YELLOW)
                    .create()
            ));
            player.spigot().sendMessage(rentButton);
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage("");
    }

    /**
     * Nettoyer les données quand un joueur quitte
     */
    public void removePlayer(UUID playerUuid) {
        lastTerritoryState.remove(playerUuid);
    }
}
