package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotGroup;
import com.gravityyfh.roleplaycity.town.data.TerritoryEntity;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

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

    // Scoreboards actifs pour chaque joueur
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();

    // Tâches d'affichage automatique des contours (pour nettoyage)
    private final Map<UUID, Integer> autoDisplayTasks = new HashMap<>();

    // Couleurs pour les particules d'affichage automatique
    private static final Particle.DustOptions COLOR_FOR_SALE = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.5f);  // Vert
    private static final Particle.DustOptions COLOR_FOR_RENT = new Particle.DustOptions(Color.fromRGB(0, 200, 255), 1.5f); // Bleu

    public TownHUDListener(RoleplayCity plugin, TownManager townManager, ClaimManager claimManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.claimManager = claimManager;
    }

    /**
     * Nettoie le scoreboard quand un joueur se déconnecte
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        lastTerritoryState.remove(playerId);
        playerScoreboards.remove(playerId);
        stopAutoDisplay(playerId);
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

            // ⚠️ NOUVEAU SYSTÈME : Chercher d'abord un plot individuel, puis un PlotGroup
            Plot plot = town.getPlot(toChunk.getWorld().getName(), toChunk.getX(), toChunk.getZ());
            PlotGroup plotGroup = null;

            if (plot == null) {
                // Pas de plot individuel, chercher un PlotGroup
                plotGroup = town.getPlotGroupAt(toChunk.getWorld().getName(), toChunk.getX(), toChunk.getZ());
                if (plotGroup == null) {
                    return; // Ni plot ni groupe trouvé
                }
            }

            // ⚠️ NOUVEAU SYSTÈME : Utiliser TerritoryEntity (Plot OU PlotGroup)
            TerritoryEntity territory = (plot != null) ? plot : plotGroup;
            boolean isInGroup = (plot == null); // Si pas de plot individuel, c'est un groupe

            // Construire l'info du territoire pour comparaison
            StringBuilder plotInfo = new StringBuilder();
            plotInfo.append(territory.getType().name());
            if (territory.getOwnerUuid() != null) {
                plotInfo.append(":").append(territory.getOwnerName());
            }
            if (territory.isMunicipal() && territory.getMunicipalSubType() != null) {
                plotInfo.append(":").append(territory.getMunicipalSubType().name());
            }
            if (territory.isForSale()) {
                plotInfo.append(":VENTE:").append(territory.getSalePrice());
            }
            if (territory.isForRent()) {
                plotInfo.append(":LOCATION:").append(territory.getRentPricePerDay());
            }
            if (territory.getRenterUuid() != null) {
                plotInfo.append(":LOUE:").append(territory.getRentDaysRemaining());
            }

            newState = new TerritoryState(claimingTown, plotInfo.toString());

            // Si plot individuel, vérifier s'il fait partie d'un groupe
            if (plot != null && plotGroup == null) {
                plotGroup = town.findPlotGroupByPlot(plot);
                if (plotGroup != null) {
                    isInGroup = true;
                }
            }

            // Construire le message HUD (ActionBar)
            StringBuilder hud = new StringBuilder();
            hud.append(ChatColor.GOLD);
            hud.append(ChatColor.AQUA).append(claimingTown);
            hud.append(ChatColor.GRAY).append(" | ");

            // Afficher la surface (sauf pour MUNICIPAL et PUBLIC)
            com.gravityyfh.roleplaycity.town.data.PlotType plotType = territory.getType();
            boolean shouldShowSurface = (plotType != com.gravityyfh.roleplaycity.town.data.PlotType.MUNICIPAL &&
                                        plotType != com.gravityyfh.roleplaycity.town.data.PlotType.PUBLIC);

            if (shouldShowSurface) {
                if (isInGroup) {
                    // Afficher la surface totale du groupe
                    int totalSurface = plotGroup.getChunkKeys().size() * 256;
                    hud.append(ChatColor.WHITE).append(totalSurface).append("m²");
                } else {
                    // Afficher la surface de la parcelle individuelle
                    hud.append(ChatColor.WHITE).append("256m²");
                }
                hud.append(ChatColor.GRAY).append(" | ");
            }

            // Si groupé, afficher le nom du groupe avant le type
            if (isInGroup) {
                hud.append(ChatColor.LIGHT_PURPLE).append(plotGroup.getGroupName());
                hud.append(ChatColor.GRAY).append(" | ");
            }

            hud.append(territory.getType().getDisplayName());

            // Ajouter le propriétaire si c'est une parcelle privée
            if (territory.getOwnerUuid() != null) {
                String displayName;

                // Si terrain PROFESSIONNEL avec entreprise : afficher le nom de l'entreprise
                if (territory.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && territory.getCompanySiret() != null) {
                    com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                        .getCompanyBySiret(territory.getCompanySiret());
                    displayName = (ownerCompany != null) ? ownerCompany.getNom() : (territory.getOwnerName() != null ? territory.getOwnerName() : "Inconnu");
                } else {
                    // Terrain PARTICULIER : afficher le nom du joueur
                    displayName = territory.getOwnerName() != null ? territory.getOwnerName() : "Inconnu";
                }

                hud.append(ChatColor.GRAY).append(" - ");
                hud.append(ChatColor.YELLOW).append(displayName);
            }

            // Ajouter le sous-type si municipal
            if (territory.isMunicipal() && territory.getMunicipalSubType() != null) {
                hud.append(ChatColor.GRAY).append(" (");
                hud.append(territory.getMunicipalSubType().getDisplayName());
                hud.append(ChatColor.GRAY).append(")");
            }

            // Ajouter statut vente/location (utiliser territory directement)
            boolean hudForSale = territory.isForSale();
            boolean hudForRent = territory.isForRent();
            double hudSalePrice = territory.getSalePrice();
            double hudRentPrice = territory.getRentPricePerDay();
            java.util.UUID hudRenterUuid = territory.getRenterUuid();
            int hudRentDays = territory.getRentDaysRemaining();

            if (hudForSale) {
                hud.append(ChatColor.GREEN).append(" [À VENDRE: ").append(String.format("%.0f€", hudSalePrice)).append("]");
            }
            if (hudForRent) {
                hud.append(ChatColor.AQUA).append(" [LOCATION: ").append(String.format("%.0f€/j", hudRentPrice)).append("]");
            }
            if (hudRenterUuid != null) {
                hud.append(ChatColor.LIGHT_PURPLE).append(" [Loué: ").append(hudRentDays).append("j]");
            }

            displayMessage = hud.toString();

            // Envoyer message détaillé dans le chat si en vente ou location
            boolean isForSale = territory.isForSale();
            boolean isForRent = territory.isForRent();

            if (isForSale || isForRent) {
                // ⚠️ NOUVEAU SYSTÈME : Passer territory au lieu de plot
                sendDetailedPlotInfo(player, territory, claimingTown);

                // Démarrer l'affichage automatique du contour
                startAutoDisplay(player, territory, town, isForSale, isForRent);
            }
        } else {
            // Chunk non claim (zone sauvage)
            newState = new TerritoryState(null, null);
            displayMessage = ChatColor.GREEN + "Zone Sauvage" + ChatColor.GRAY + " | Non revendiqué";
        }

        // Comparer avec l'état précédent
        TerritoryState lastState = lastTerritoryState.get(player.getUniqueId());

        // Gestion des transitions et du scoreboard
        if (lastState == null || !lastState.equals(newState)) {
            boolean wasInTown = (lastState != null && lastState.townName != null);
            boolean isInTown = (newState.townName != null);

            // Message d'entrée dans une ville
            if (!wasInTown && isInTown) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "  Vous entrez dans " + ChatColor.AQUA + claimingTown);
                player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("");
            }
            // Message de sortie d'une ville
            else if (wasInTown && !isInTown) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "  Vous quittez " + ChatColor.AQUA + lastState.townName);
                player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("");
                removeScoreboard(player);
            }
            // Changement de ville
            else if (wasInTown && isInTown && !lastState.townName.equals(newState.townName)) {
                player.sendMessage("");
                player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "  Vous quittez " + ChatColor.AQUA + lastState.townName);
                player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "  Vous entrez dans " + ChatColor.AQUA + claimingTown);
                player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                player.sendMessage("");
            }

            // Mettre à jour ou créer le scoreboard si dans une ville
            if (isInTown) {
                Town town = townManager.getTown(claimingTown);
                if (town != null) {
                    // ⚠️ NOUVEAU SYSTÈME : Chercher Plot OU PlotGroup
                    Plot plotForScoreboard = town.getPlot(toChunk.getWorld().getName(), toChunk.getX(), toChunk.getZ());
                    PlotGroup groupForScoreboard = null;
                    TerritoryEntity territoryForScoreboard = null;

                    if (plotForScoreboard != null) {
                        territoryForScoreboard = plotForScoreboard;
                    } else {
                        // Pas de plot individuel, chercher un PlotGroup
                        groupForScoreboard = town.getPlotGroupAt(toChunk.getWorld().getName(), toChunk.getX(), toChunk.getZ());
                        if (groupForScoreboard != null) {
                            territoryForScoreboard = groupForScoreboard;
                        }
                    }

                    if (territoryForScoreboard != null) {
                        // Créer le scoreboard (fonctionne avec Plot ET PlotGroup)
                        updateScoreboard(player, town, territoryForScoreboard);

                        // Envoyer message détaillé dans le chat si en vente ou location
                        boolean forSale = territoryForScoreboard.isForSale();
                        boolean forRent = territoryForScoreboard.isForRent();

                        if (forSale || forRent) {
                            sendDetailedPlotInfo(player, territoryForScoreboard, claimingTown);

                            // Démarrer l'affichage automatique du contour
                            startAutoDisplay(player, territoryForScoreboard, town, forSale, forRent);
                        }
                    }
                }
            }

            lastTerritoryState.put(player.getUniqueId(), newState);
        }
    }

    /**
     * Surcharge pour TerritoryEntity (Plot OU PlotGroup)
     */
    private void sendDetailedPlotInfo(Player player, TerritoryEntity territory, String townName) {
        if (territory instanceof Plot) {
            sendDetailedPlotInfo(player, (Plot) territory, townName);
        } else if (territory instanceof PlotGroup) {
            sendDetailedPlotGroupInfo(player, (PlotGroup) territory, townName);
        }
    }

    /**
     * Envoie un message détaillé dans le chat pour les parcelles en vente/location
     */
    private void sendDetailedPlotInfo(Player player, Plot plot, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) return;

        // Vérifier si la parcelle fait partie d'un groupe
        PlotGroup plotGroup = town.findPlotGroupByPlot(plot);
        boolean isInGroup = (plotGroup != null);
        int totalSurface = isInGroup ? (plotGroup.getChunkKeys().size() * 256) : 256;

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");

        // Vérifier vente/location du groupe ou de la parcelle individuelle
        boolean isForSale = isInGroup ? plotGroup.isForSale() : plot.isForSale();
        boolean isForRent = isInGroup ? plotGroup.isForRent() : plot.isForRent();

        if (isForSale) {
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + (isInGroup ? "GROUPE À VENDRE" : "PARCELLE À VENDRE"));
        } else if (isForRent) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + (isInGroup ? "GROUPE EN LOCATION" : "PARCELLE EN LOCATION"));
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");

        // Informations de base
        player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + plot.getType().getDisplayName());

        // Afficher la surface (sauf pour MUNICIPAL et PUBLIC)
        com.gravityyfh.roleplaycity.town.data.PlotType plotType = plot.getType();
        boolean shouldShowSurface = (plotType != com.gravityyfh.roleplaycity.town.data.PlotType.MUNICIPAL &&
                                    plotType != com.gravityyfh.roleplaycity.town.data.PlotType.PUBLIC);

        if (shouldShowSurface) {
            if (isInGroup) {
                player.sendMessage(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + totalSurface + " m² " +
                    ChatColor.GRAY + "(" + plotGroup.getChunkKeys().size() + " parcelles groupées)");
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Groupe: " + ChatColor.WHITE + plotGroup.getGroupName());
            } else {
                player.sendMessage(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + "256 m² " + ChatColor.GRAY + "(16x16)");
            }
        } else if (isInGroup) {
            // Si municipal/public mais dans un groupe, afficher quand même le nom du groupe
            player.sendMessage(ChatColor.LIGHT_PURPLE + "Groupe: " + ChatColor.WHITE + plotGroup.getGroupName());
        }

        player.sendMessage(ChatColor.YELLOW + "Position: " + ChatColor.WHITE + "X: " + (plot.getChunkX() * 16) + " Z: " + (plot.getChunkZ() * 16));

        // Propriétaire / Entreprise
        if (plot.getOwnerUuid() != null) {
            // Si terrain PROFESSIONNEL avec entreprise : afficher infos complètes de l'entreprise
            if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(plot.getCompanySiret());

                if (ownerCompany != null) {
                    player.sendMessage(ChatColor.YELLOW + "Entreprise: " + ChatColor.WHITE + ownerCompany.getNom());
                    player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + ownerCompany.getType());
                    player.sendMessage(ChatColor.YELLOW + "Gérant: " + ChatColor.WHITE + ownerCompany.getGerant());
                    player.sendMessage(ChatColor.YELLOW + "SIRET: " + ChatColor.WHITE + ownerCompany.getSiret());
                } else {
                    // Fallback si entreprise non trouvée
                    player.sendMessage(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + plot.getOwnerName());
                }
            } else {
                // Terrain PARTICULIER : afficher le nom du joueur
                player.sendMessage(ChatColor.YELLOW + "Propriétaire: " + ChatColor.WHITE + plot.getOwnerName());
            }
        }

        player.sendMessage("");

        // Informations de vente (groupe ou parcelle individuelle)
        if (isForSale) {
            double salePrice = isInGroup ? plotGroup.getSalePrice() : plot.getSalePrice();
            player.sendMessage(ChatColor.GREEN + "Prix de vente: " + ChatColor.GOLD + String.format("%.2f€", salePrice));
            player.sendMessage("");

            // Bouton cliquable pour acheter
            String buttonText = isInGroup ? "  [ACHETER CE GROUPE]" : "  [ACHETER CETTE PARCELLE]";
            TextComponent buyButton = new TextComponent(buttonText);
            buyButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
            buyButton.setBold(true);
            buyButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/ville:buyplot " + plot.getChunkX() + " " + plot.getChunkZ() + " " + plot.getWorldName()));
            buyButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Cliquez pour acheter\nPrix: " + String.format("%.2f€", salePrice))
                    .color(net.md_5.bungee.api.ChatColor.YELLOW)
                    .create()
            ));
            player.spigot().sendMessage(buyButton);
        }

        // Informations de location (groupe ou parcelle individuelle)
        if (isForRent) {
            double rentPrice = isInGroup ? plotGroup.getRentPricePerDay() : plot.getRentPricePerDay();
            UUID renterUuid = isInGroup ? plotGroup.getRenterUuid() : plot.getRenterUuid();
            int rentDays = isInGroup ? plotGroup.getRentDaysRemaining() : plot.getRentDaysRemaining();

            player.sendMessage(ChatColor.AQUA + "Prix de location: " + ChatColor.GOLD + String.format("%.2f€/jour", rentPrice));
            player.sendMessage(ChatColor.GRAY + "Solde maximum: 30 jours rechargeable");

            if (renterUuid != null) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Actuellement loué: " + rentDays + " jours restants");
            } else {
                player.sendMessage(ChatColor.GREEN + "Disponible immédiatement");
            }

            player.sendMessage("");

            // Bouton cliquable pour louer
            String buttonText = isInGroup ? "  [LOUER CE GROUPE]" : "  [LOUER CETTE PARCELLE]";
            TextComponent rentButton = new TextComponent(buttonText);
            rentButton.setColor(net.md_5.bungee.api.ChatColor.AQUA);
            rentButton.setBold(true);
            rentButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                "/ville:rentplot " + plot.getChunkX() + " " + plot.getChunkZ() + " " + plot.getWorldName()));
            rentButton.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder("Cliquez pour louer\nPrix: " + String.format("%.2f€/jour", rentPrice))
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
        playerScoreboards.remove(playerUuid);
    }

    /**
     * Crée ou met à jour le scoreboard d'un joueur avec les infos du terrain actuel
     */
    /**
     * Surcharge pour TerritoryEntity (Plot OU PlotGroup)
     */
    private void updateScoreboard(Player player, Town town, TerritoryEntity territory) {
        if (territory instanceof Plot) {
            updateScoreboard(player, town, (Plot) territory);
        } else if (territory instanceof PlotGroup) {
            updateScoreboardForGroup(player, town, (PlotGroup) territory);
        }
    }

    private void updateScoreboard(Player player, Town town, Plot plot) {
        // Créer un nouveau scoreboard ou réutiliser l'existant
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            playerScoreboards.put(player.getUniqueId(), scoreboard);
        }

        // Supprimer l'ancien objectif s'il existe
        Objective objective = scoreboard.getObjective("townInfo");
        if (objective != null) {
            objective.unregister();
        }

        // Créer un nouvel objectif
        objective = scoreboard.registerNewObjective("townInfo", "dummy",
            ChatColor.GOLD + "" + ChatColor.BOLD + town.getName());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Vérifier si la parcelle fait partie d'un groupe
        com.gravityyfh.roleplaycity.town.data.PlotGroup plotGroup = town.findPlotGroupByPlot(plot);
        boolean isInGroup = (plotGroup != null);
        int totalSurface = isInGroup ? (plotGroup.getChunkKeys().size() * 256) : 256;

        int line = 15; // Commencer du haut

        // Ligne vide pour l'espacement
        objective.getScore(ChatColor.DARK_GRAY + " ").setScore(line--);

        // SECTION TERRAIN
        objective.getScore(ChatColor.YELLOW + "" + ChatColor.BOLD + "Terrain").setScore(line--);

        // Type de terrain
        objective.getScore(" " + ChatColor.GRAY + "Type: " + ChatColor.WHITE + plot.getType().getDisplayName()).setScore(line--);

        // Surface (sauf pour MUNICIPAL et PUBLIC)
        com.gravityyfh.roleplaycity.town.data.PlotType plotType = plot.getType();
        boolean shouldShowSurface = (plotType != com.gravityyfh.roleplaycity.town.data.PlotType.MUNICIPAL &&
                                    plotType != com.gravityyfh.roleplaycity.town.data.PlotType.PUBLIC);

        if (shouldShowSurface) {
            if (isInGroup) {
                objective.getScore(" " + ChatColor.GRAY + "Surface: " + ChatColor.WHITE + totalSurface + "m²").setScore(line--);
                objective.getScore(" " + ChatColor.LIGHT_PURPLE + plotGroup.getGroupName()).setScore(line--);
            } else {
                objective.getScore(" " + ChatColor.GRAY + "Surface: " + ChatColor.WHITE + "256m²").setScore(line--);
            }
        } else if (isInGroup) {
            // Si municipal/public mais dans un groupe, afficher quand même le nom du groupe
            objective.getScore(" " + ChatColor.LIGHT_PURPLE + plotGroup.getGroupName()).setScore(line--);
        }

        // Propriétaire / Entreprise
        if (plot.getOwnerUuid() != null) {
            // Terrain a un propriétaire
            if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                // Terrain PROFESSIONNEL → afficher infos entreprise complètes
                com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(plot.getCompanySiret());

                if (ownerCompany != null) {
                    String companyName = ownerCompany.getNom();
                    if (companyName.length() > 14) {
                        companyName = companyName.substring(0, 14) + "...";
                    }

                    String companyType = ownerCompany.getType();
                    if (companyType.length() > 14) {
                        companyType = companyType.substring(0, 14) + "...";
                    }

                    String ownerName = ownerCompany.getGerant();
                    if (ownerName.length() > 12) {
                        ownerName = ownerName.substring(0, 12) + "...";
                    }

                    String siret = ownerCompany.getSiret();
                    String siretShort = siret.length() > 10 ? siret.substring(0, 10) + "..." : siret;

                    objective.getScore(" " + ChatColor.GRAY + "Entreprise:").setScore(line--);
                    objective.getScore(" " + ChatColor.GOLD + companyName).setScore(line--);
                    objective.getScore(" " + ChatColor.GRAY + "Type: " + ChatColor.WHITE + companyType).setScore(line--);
                    objective.getScore(" " + ChatColor.GRAY + "Gérant: " + ChatColor.YELLOW + ownerName).setScore(line--);
                    objective.getScore(" " + ChatColor.GRAY + "SIRET: " + ChatColor.WHITE + siretShort).setScore(line--);
                } else {
                    // Fallback si l'entreprise n'est pas trouvée
                    String companyName = plot.getCompanyName();
                    if (companyName != null && companyName.length() > 12) {
                        companyName = companyName.substring(0, 12) + "...";
                    }
                    String ownerName = plot.getOwnerName();
                    if (ownerName != null && ownerName.length() > 12) {
                        ownerName = ownerName.substring(0, 12) + "...";
                    }

                    objective.getScore(" " + ChatColor.GRAY + "Entreprise:").setScore(line--);
                    objective.getScore(" " + ChatColor.GOLD + companyName).setScore(line--);
                    objective.getScore(" " + ChatColor.GRAY + "Gérant: " + ChatColor.YELLOW + ownerName).setScore(line--);
                    objective.getScore(" " + ChatColor.GRAY + "SIRET: " + ChatColor.WHITE + plot.getCompanySiret().substring(0, 8) + "...").setScore(line--);
                }
            } else {
                // Terrain PARTICULIER → afficher nom proprio
                String ownerName = plot.getOwnerName();
                if (ownerName.length() > 12) {
                    ownerName = ownerName.substring(0, 12) + "...";
                }
                objective.getScore(" " + ChatColor.GRAY + "Proprio: " + ChatColor.YELLOW + ownerName).setScore(line--);
            }
        }
        // Si ownerUuid == null, le terrain appartient à la ville (pas de ligne affichée)

        // Si terrain loué, afficher info locataire entreprise
        if (plot.getRenterUuid() != null) {
            if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && plot.getRenterCompanySiret() != null) {
                // Locataire avec entreprise
                com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise renterCompany = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(plot.getRenterCompanySiret());

                if (renterCompany != null) {
                    String renterCompanyName = renterCompany.getNom();
                    if (renterCompanyName.length() > 14) {
                        renterCompanyName = renterCompanyName.substring(0, 14) + "...";
                    }

                    String renterCompanyType = renterCompany.getType();
                    if (renterCompanyType.length() > 14) {
                        renterCompanyType = renterCompanyType.substring(0, 14) + "...";
                    }

                    String renterGerant = renterCompany.getGerant();
                    if (renterGerant.length() > 12) {
                        renterGerant = renterGerant.substring(0, 12) + "...";
                    }

                    String siret = renterCompany.getSiret();
                    String siretShort = siret.length() > 10 ? siret.substring(0, 10) + "..." : siret;

                    objective.getScore(" " + ChatColor.AQUA + "Loué par:").setScore(line--);
                    objective.getScore(" " + ChatColor.LIGHT_PURPLE + renterCompanyName).setScore(line--);
                    objective.getScore(" " + ChatColor.GRAY + "Type: " + ChatColor.WHITE + renterCompanyType).setScore(line--);
                    objective.getScore(" " + ChatColor.GRAY + "Gérant: " + ChatColor.YELLOW + renterGerant).setScore(line--);
                    objective.getScore(" " + ChatColor.GRAY + "SIRET: " + ChatColor.WHITE + siretShort).setScore(line--);
                }
            }
        }

        // Ligne vide
        objective.getScore(ChatColor.DARK_GRAY + "  ").setScore(line--);

        // SECTION ÉCONOMIE (si applicable)
        // Si dans un groupe, utiliser les infos du groupe, sinon celles de la parcelle
        boolean hasEconomyInfo = false;
        boolean forSale = false;
        boolean forRent = false;
        double salePrice = 0;
        double rentPrice = 0;
        UUID renterUuid = null;
        int rentDays = 0;

        if (isInGroup) {
            forSale = plotGroup.isForSale();
            forRent = plotGroup.isForRent();
            salePrice = plotGroup.getSalePrice();
            rentPrice = plotGroup.getRentPricePerDay();
            renterUuid = plotGroup.getRenterUuid();
            rentDays = plotGroup.getRentDaysRemaining();
            hasEconomyInfo = forSale || forRent || renterUuid != null;
        } else {
            forSale = plot.isForSale();
            forRent = plot.isForRent();
            salePrice = plot.getSalePrice();
            rentPrice = plot.getRentPricePerDay();
            renterUuid = plot.getRenterUuid();
            rentDays = plot.getRentDaysRemaining();
            hasEconomyInfo = forSale || forRent || renterUuid != null;
        }

        if (hasEconomyInfo) {
            objective.getScore(ChatColor.YELLOW + "" + ChatColor.BOLD + "Economie").setScore(line--);

            if (forSale) {
                objective.getScore(" " + ChatColor.GREEN + "À vendre: " + ChatColor.GOLD + String.format("%.0f€", salePrice)).setScore(line--);
            }

            if (forRent) {
                objective.getScore(" " + ChatColor.AQUA + "Location: " + ChatColor.WHITE + String.format("%.0f€/j", rentPrice)).setScore(line--);
            }

            if (renterUuid != null) {
                // Récupérer le SIRET du locataire depuis la parcelle actuelle
                // (même si dans un groupe, toutes les parcelles partagent le même locataire)
                String renterCompanySiret = plot.getRenterCompanySiret();

                // Si c'est un terrain PROFESSIONNEL avec une entreprise locataire, afficher les infos de l'entreprise
                if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && renterCompanySiret != null) {
                    com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise renterCompany = plugin.getCompanyPlotManager()
                        .getCompanyBySiret(renterCompanySiret);

                    if (renterCompany != null) {
                        String companyName = renterCompany.getNom();
                        if (companyName.length() > 14) {
                            companyName = companyName.substring(0, 14) + "...";
                        }
                        objective.getScore(" " + ChatColor.LIGHT_PURPLE + "Loué: " + ChatColor.WHITE + companyName).setScore(line--);
                    } else {
                        // Fallback si l'entreprise n'est pas trouvée
                        String renterName = org.bukkit.Bukkit.getOfflinePlayer(renterUuid).getName();
                        if (renterName != null && renterName.length() > 12) {
                            renterName = renterName.substring(0, 12) + "...";
                        }
                        objective.getScore(" " + ChatColor.LIGHT_PURPLE + "Loué par: " + ChatColor.WHITE + renterName).setScore(line--);
                    }
                } else {
                    // Terrain PARTICULIER ou pas d'entreprise : afficher le nom du joueur
                    String renterName = org.bukkit.Bukkit.getOfflinePlayer(renterUuid).getName();
                    if (renterName != null && renterName.length() > 12) {
                        renterName = renterName.substring(0, 12) + "...";
                    }
                    objective.getScore(" " + ChatColor.LIGHT_PURPLE + "Loué par: " + ChatColor.WHITE + renterName).setScore(line--);
                }

                objective.getScore(" " + ChatColor.GRAY + "Jours: " + ChatColor.WHITE + rentDays + "j").setScore(line--);
            }

            // Ligne vide
            objective.getScore(ChatColor.DARK_GRAY + "   ").setScore(line--);
        }

        // Appliquer le scoreboard au joueur
        player.setScoreboard(scoreboard);
    }

    /**
     * ⚠️ NOUVEAU SYSTÈME : Créé un scoreboard pour un PlotGroup
     */
    private void updateScoreboardForGroup(Player player, Town town, PlotGroup group) {
        // Créer un nouveau scoreboard ou réutiliser l'existant
        Scoreboard scoreboard = playerScoreboards.get(player.getUniqueId());
        if (scoreboard == null) {
            scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
            playerScoreboards.put(player.getUniqueId(), scoreboard);
        }

        // Supprimer l'ancien objectif s'il existe
        Objective objective = scoreboard.getObjective("townInfo");
        if (objective != null) {
            objective.unregister();
        }

        // Créer un nouvel objectif
        objective = scoreboard.registerNewObjective("townInfo", "dummy",
            ChatColor.GOLD + "" + ChatColor.BOLD + town.getName());
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 15; // Commencer du haut

        // Ligne vide pour l'espacement
        objective.getScore(ChatColor.DARK_GRAY + " ").setScore(line--);

        // SECTION TERRAIN
        objective.getScore(ChatColor.YELLOW + "" + ChatColor.BOLD + "Terrain").setScore(line--);

        // Type de terrain
        objective.getScore(" " + ChatColor.GRAY + "Type: " + ChatColor.WHITE + group.getType().getDisplayName()).setScore(line--);

        // Nom du groupe
        objective.getScore(" " + ChatColor.LIGHT_PURPLE + group.getGroupName()).setScore(line--);

        // Surface (sauf pour MUNICIPAL et PUBLIC)
        boolean shouldShowSurface = (group.getType() != com.gravityyfh.roleplaycity.town.data.PlotType.MUNICIPAL &&
                                    group.getType() != com.gravityyfh.roleplaycity.town.data.PlotType.PUBLIC);

        if (shouldShowSurface) {
            int totalSurface = group.getChunkCount() * 256;
            objective.getScore(" " + ChatColor.GRAY + "Surface: " + ChatColor.WHITE + totalSurface + "m²").setScore(line--);
        }

        // Propriétaire
        if (group.getOwnerUuid() != null) {
            String ownerName = group.getOwnerName() != null ? group.getOwnerName() : "Inconnu";
            if (ownerName.length() > 14) {
                ownerName = ownerName.substring(0, 14) + "...";
            }

            // Si PROFESSIONNEL, afficher l'entreprise
            if (group.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL && group.getCompanySiret() != null) {
                com.gravityyfh.roleplaycity.EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                    .getCompanyBySiret(group.getCompanySiret());

                if (ownerCompany != null) {
                    String companyName = ownerCompany.getNom();
                    if (companyName.length() > 14) {
                        companyName = companyName.substring(0, 14) + "...";
                    }
                    objective.getScore(" " + ChatColor.GRAY + "Entreprise: " + ChatColor.YELLOW + companyName).setScore(line--);
                } else {
                    objective.getScore(" " + ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + ownerName).setScore(line--);
                }
            } else {
                objective.getScore(" " + ChatColor.GRAY + "Propriétaire: " + ChatColor.YELLOW + ownerName).setScore(line--);
            }
        }

        // Ligne vide
        objective.getScore(ChatColor.DARK_GRAY + "  ").setScore(line--);

        // SECTION VILLE
        objective.getScore(ChatColor.AQUA + "" + ChatColor.BOLD + "Ville").setScore(line--);

        // Obtenir le nom du maire
        String mayorName = org.bukkit.Bukkit.getOfflinePlayer(town.getMayorUuid()).getName();
        if (mayorName == null) {
            mayorName = "Inconnu";
        } else if (mayorName.length() > 14) {
            mayorName = mayorName.substring(0, 14) + "...";
        }
        objective.getScore(" " + ChatColor.GRAY + "Maire: " + ChatColor.WHITE + mayorName).setScore(line--);
        objective.getScore(" " + ChatColor.GRAY + "Citoyens: " + ChatColor.WHITE + town.getMembers().size()).setScore(line--);

        // Appliquer le scoreboard au joueur
        player.setScoreboard(scoreboard);
    }

    /**
     * Supprime le scoreboard d'un joueur (quand il quitte une ville)
     */
    private void removeScoreboard(Player player) {
        playerScoreboards.remove(player.getUniqueId());
        // Réinitialiser au scoreboard par défaut
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    /**
     * Surcharge pour TerritoryEntity (Plot OU PlotGroup)
     */
    private void startAutoDisplay(Player player, TerritoryEntity territory, Town town, boolean isForSale, boolean isForRent) {
        if (territory instanceof Plot) {
            startAutoDisplay(player, (Plot) territory, town, isForSale, isForRent);
        } else if (territory instanceof PlotGroup) {
            startAutoDisplayGroup(player, (PlotGroup) territory, town, isForSale, isForRent);
        }
    }

    /**
     * Démarre l'affichage automatique du contour d'un terrain
     * L'affichage dure 15 secondes puis s'arrête automatiquement
     */
    private void startAutoDisplay(Player player, Plot plot, Town town, boolean isForSale, boolean isForRent) {
        UUID playerUuid = player.getUniqueId();

        // Arrêter l'affichage précédent s'il existe
        stopAutoDisplay(playerUuid);

        // Choisir la couleur selon le statut
        Particle.DustOptions color = isForSale ? COLOR_FOR_SALE : COLOR_FOR_RENT;

        // Déterminer quelles parcelles afficher (groupe ou parcelle individuelle)
        com.gravityyfh.roleplaycity.town.data.PlotGroup plotGroup = town.findPlotGroupByPlot(plot);
        java.util.Set<String> plotKeys = new java.util.HashSet<>();

        if (plotGroup != null) {
            // ⚠️ NOUVEAU SYSTÈME : Utiliser getChunkKeys() au lieu de getPlotKeys()
            plotKeys.addAll(plotGroup.getChunkKeys());
        } else {
            // Afficher seulement la parcelle
            plotKeys.add(plot.getWorldName() + ":" + plot.getChunkX() + ":" + plot.getChunkZ());
        }

        // Créer une tâche répétitive pour afficher les particules
        int taskId = new BukkitRunnable() {
            int ticksRemaining = 300; // 15 secondes * 20 ticks

            @Override
            public void run() {
                if (!player.isOnline() || ticksRemaining <= 0) {
                    this.cancel();
                    stopAutoDisplay(playerUuid);
                    return;
                }

                // Afficher les particules pour chaque parcelle
                for (String plotKey : plotKeys) {
                    String[] parts = plotKey.split(":");
                    if (parts.length == 3) {
                        String worldName = parts[0];
                        int chunkX = Integer.parseInt(parts[1]);
                        int chunkZ = Integer.parseInt(parts[2]);
                        displayPlotBorder(worldName, chunkX, chunkZ, color, plotKeys);
                    }
                }

                ticksRemaining -= 10; // Décrémenter de 10 ticks (0.5 seconde)
            }
        }.runTaskTimer(plugin, 0L, 10L).getTaskId(); // Répéter toutes les 0.5 secondes

        autoDisplayTasks.put(playerUuid, taskId);
    }

    /**
     * Arrête l'affichage automatique pour un joueur
     */
    private void stopAutoDisplay(UUID playerUuid) {
        Integer taskId = autoDisplayTasks.remove(playerUuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    /**
     * Affiche le contour d'une parcelle avec des particules
     * Utilise le système "connected borders" pour ne pas afficher les bordures internes
     */
    private void displayPlotBorder(String worldName, int chunkX, int chunkZ, Particle.DustOptions color, java.util.Set<String> groupPlotKeys) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        int startX = chunkX * 16;
        int startZ = chunkZ * 16;

        // Vérifier quels côtés doivent être affichés (connected borders)
        boolean showNorth = !groupPlotKeys.contains(worldName + ":" + chunkX + ":" + (chunkZ - 1));
        boolean showEast = !groupPlotKeys.contains(worldName + ":" + (chunkX + 1) + ":" + chunkZ);
        boolean showSouth = !groupPlotKeys.contains(worldName + ":" + chunkX + ":" + (chunkZ + 1));
        boolean showWest = !groupPlotKeys.contains(worldName + ":" + (chunkX - 1) + ":" + chunkZ);

        // Côté Nord
        if (showNorth) {
            for (int x = startX; x < startX + 16; x++) {
                spawnParticleColumn(world, x + 0.5, startZ + 0.5, color);
            }
        }

        // Côté Est
        if (showEast) {
            for (int z = startZ; z < startZ + 16; z++) {
                spawnParticleColumn(world, startX + 16.0, z + 0.5, color);
            }
        }

        // Côté Sud
        if (showSouth) {
            for (int x = startX; x < startX + 16; x++) {
                spawnParticleColumn(world, x + 0.5, startZ + 16.0, color);
            }
        }

        // Côté Ouest
        if (showWest) {
            for (int z = startZ; z < startZ + 16; z++) {
                spawnParticleColumn(world, startX + 0.0, z + 0.5, color);
            }
        }
    }

    /**
     * Spawn une colonne de particules à une position donnée
     */
    private void spawnParticleColumn(World world, double x, double z, Particle.DustOptions color) {
        int groundY = world.getHighestBlockYAt((int) x, (int) z);

        // Spawn 5 particules espacées verticalement
        for (int i = 0; i < 5; i++) {
            double y = groundY + 0.2 + (i * 0.4);

            // Spawn 3 particules pour plus de densité
            for (int j = 0; j < 3; j++) {
                world.spawnParticle(
                    Particle.REDSTONE,
                    x + (Math.random() * 0.2 - 0.1),
                    y,
                    z + (Math.random() * 0.2 - 0.1),
                    1,
                    0, 0, 0, 0,
                    color
                );
            }
        }
    }

    /**
     * ⚠️ NOUVEAU SYSTÈME : Affiche les informations détaillées d'un PlotGroup
     * TODO: Implémenter une vraie version avec toutes les infos du groupe
     */
    private void sendDetailedPlotGroupInfo(Player player, PlotGroup group, String townName) {
        // Pour l'instant, juste un placeholder basique
        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        if (group.isForSale()) {
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "GROUPE À VENDRE");
        } else if (group.isForRent()) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "GROUPE EN LOCATION");
        }
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + group.getType().getDisplayName());
        player.sendMessage(ChatColor.YELLOW + "Groupe: " + ChatColor.LIGHT_PURPLE + group.getGroupName());
        player.sendMessage(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + (group.getChunkCount() * 256) + " m² " +
                ChatColor.GRAY + "(" + group.getChunkCount() + " parcelles)");

        if (group.isForSale()) {
            player.sendMessage(ChatColor.GREEN + "Prix de vente: " + ChatColor.WHITE + String.format("%.0f€", group.getSalePrice()));
        }
        if (group.isForRent()) {
            player.sendMessage(ChatColor.AQUA + "Location: " + ChatColor.WHITE + String.format("%.0f€/jour", group.getRentPricePerDay()));
        }
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");
        player.sendMessage("");
    }

    /**
     * ⚠️ NOUVEAU SYSTÈME : Affiche le contour d'un PlotGroup
     * TODO: Implémenter l'affichage des particules pour tous les chunks du groupe
     */
    private void startAutoDisplayGroup(Player player, PlotGroup group, Town town, boolean isForSale, boolean isForRent) {
        // Pour l'instant, ne rien faire (évite les particules sur tous les chunks ce qui lag)
        // On peut implémenter ça plus tard si besoin
    }
}
