package com.gravityyfh.roleplaycity.town.listener;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
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
    private static final Particle.DustOptions COLOR_FOR_SALE = new Particle.DustOptions(Color.fromRGB(40, 40, 40), 0.7f);  // Gris foncé
    private static final Particle.DustOptions COLOR_FOR_RENT = new Particle.DustOptions(Color.fromRGB(40, 40, 40), 0.7f); // Gris foncé

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

            // Récupérer le plot via ClaimManager (retourne Plot individuel ou groupé)
            Plot plot = claimManager.getPlotAt(toChunk);
            if (plot == null) {
                return; // Aucun plot trouvé
            }

            // Construire l'info du territoire pour comparaison
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
            hud.append(ChatColor.GOLD);
            hud.append(ChatColor.AQUA).append(claimingTown);
            hud.append(ChatColor.GRAY).append(" | ");

            // Afficher la surface (sauf pour MUNICIPAL et PUBLIC)
            PlotType plotType = plot.getType();
            boolean shouldShowSurface = (plotType != PlotType.MUNICIPAL && plotType != PlotType.PUBLIC);

            if (shouldShowSurface) {
                if (plot.isGrouped()) {
                    // Afficher la surface totale du groupe
                    int totalSurface = plot.getChunks().size() * 256;
                    hud.append(ChatColor.WHITE).append(totalSurface).append("m²");
                } else {
                    // Afficher la surface de la parcelle individuelle
                    hud.append(ChatColor.WHITE).append("256m²");
                }
                hud.append(ChatColor.GRAY).append(" | ");
            }

            // Si groupé, afficher "Terrain groupé" au lieu du nom technique
            if (plot.isGrouped()) {
                hud.append(ChatColor.LIGHT_PURPLE).append("Terrain groupé");
                hud.append(ChatColor.GRAY).append(" | ");
            }

            hud.append(plot.getType().getDisplayName());

            // Ajouter le propriétaire si c'est une parcelle privée
            if (plot.getOwnerUuid() != null) {
                String displayName;

                // Si terrain PROFESSIONNEL avec entreprise : afficher le nom de l'entreprise
                if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                    EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
                        .getCompanyBySiret(plot.getCompanySiret());
                    displayName = (ownerCompany != null) ? ownerCompany.getNom() : (plot.getOwnerName() != null ? plot.getOwnerName() : "Inconnu");
                } else {
                    // Terrain PARTICULIER : afficher le nom du joueur
                    displayName = plot.getOwnerName() != null ? plot.getOwnerName() : "Inconnu";
                }

                hud.append(ChatColor.GRAY).append(" - ");
                hud.append(ChatColor.YELLOW).append(displayName);
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

                // Démarrer l'affichage automatique du contour
                startAutoDisplay(player, plot, town, plot.isForSale(), plot.isForRent());
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
                    Plot plotForScoreboard = claimManager.getPlotAt(toChunk);

                    if (plotForScoreboard != null) {
                        // Créer le scoreboard
                        updateScoreboard(player, town, plotForScoreboard);

                        // Envoyer message détaillé dans le chat si en vente ou location
                        if (plotForScoreboard.isForSale() || plotForScoreboard.isForRent()) {
                            sendDetailedPlotInfo(player, plotForScoreboard, claimingTown);

                            // Démarrer l'affichage automatique du contour
                            startAutoDisplay(player, plotForScoreboard, town, plotForScoreboard.isForSale(), plotForScoreboard.isForRent());
                        }
                    }
                }
            }

            lastTerritoryState.put(player.getUniqueId(), newState);
        } else if (claimingTown != null) {
            // Même territoire mais mise à jour du scoreboard pour le nouveau chunk
            Town town = townManager.getTown(claimingTown);
            if (town != null) {
                Plot plotForScoreboard = claimManager.getPlotAt(toChunk);
                if (plotForScoreboard != null) {
                    updateScoreboard(player, town, plotForScoreboard);
                }
            }
        }
    }

    /**
     * Envoie un message détaillé dans le chat pour les parcelles en vente/location
     */
    private void sendDetailedPlotInfo(Player player, Plot plot, String townName) {
        Town town = townManager.getTown(townName);
        if (town == null) return;

        // Utiliser isGrouped() pour détecter si terrain groupé
        boolean isGrouped = plot.isGrouped();
        int totalSurface = isGrouped ? (plot.getChunks().size() * 256) : 256;

        player.sendMessage("");
        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");

        if (plot.isForSale()) {
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + (isGrouped ? "GROUPE À VENDRE" : "PARCELLE À VENDRE"));
        } else if (plot.isForRent()) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + (isGrouped ? "GROUPE EN LOCATION" : "PARCELLE EN LOCATION"));
        }

        player.sendMessage(ChatColor.GOLD + "═══════════════════════════════════════");

        // Informations de base
        player.sendMessage(ChatColor.YELLOW + "Ville: " + ChatColor.WHITE + townName);
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + plot.getType().getDisplayName());

        // Afficher la surface (sauf pour MUNICIPAL et PUBLIC)
        PlotType plotType = plot.getType();
        boolean shouldShowSurface = (plotType != PlotType.MUNICIPAL && plotType != PlotType.PUBLIC);

        if (shouldShowSurface) {
            if (isGrouped) {
                player.sendMessage(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + totalSurface + " m² " +
                    ChatColor.GRAY + "(" + plot.getChunks().size() + " chunks groupés)");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Surface: " + ChatColor.WHITE + "256 m² " + ChatColor.GRAY + "(16x16)");
            }
        }

        player.sendMessage(ChatColor.YELLOW + "Position: " + ChatColor.WHITE + "X: " + (plot.getChunkX() * 16) + " Z: " + (plot.getChunkZ() * 16));

        // Propriétaire / Entreprise
        if (plot.getOwnerUuid() != null) {
            // Si terrain PROFESSIONNEL avec entreprise : afficher infos complètes de l'entreprise
            if (plot.getType() == PlotType.PROFESSIONNEL && plot.getCompanySiret() != null) {
                EntrepriseManagerLogic.Entreprise ownerCompany = plugin.getCompanyPlotManager()
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

        // Informations de vente
        if (plot.isForSale()) {
            double salePrice = plot.getSalePrice();
            player.sendMessage(ChatColor.GREEN + "Prix de vente: " + ChatColor.GOLD + String.format("%.2f€", salePrice));
            player.sendMessage("");

            // Bouton cliquable pour acheter
            String buttonText = isGrouped ? "  [ACHETER CE GROUPE]" : "  [ACHETER CETTE PARCELLE]";
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

        // Informations de location
        if (plot.isForRent()) {
            double rentPrice = plot.getRentPricePerDay();
            UUID renterUuid = plot.getRenterUuid();
            int rentDays = plot.getRentDaysRemaining();

            player.sendMessage(ChatColor.AQUA + "Prix de location: " + ChatColor.GOLD + String.format("%.2f€/jour", rentPrice));
            player.sendMessage(ChatColor.GRAY + "Solde maximum: 30 jours rechargeable");

            if (renterUuid != null) {
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Actuellement loué: " + rentDays + " jours restants");
            } else {
                player.sendMessage(ChatColor.GREEN + "Disponible immédiatement");
            }

            player.sendMessage("");

            // Bouton cliquable pour louer
            String buttonText = isGrouped ? "  [LOUER CE GROUPE]" : "  [LOUER CETTE PARCELLE]";
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
    private void updateScoreboard(Player player, Town town, Plot plot) {
        // Ne pas modifier le scoreboard si le joueur a un scoreboard médical actif
        if (plugin.getMedicalSystemManager() != null &&
            plugin.getMedicalSystemManager().hasMedicalScoreboard(player)) {
            return;
        }

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

        // Utiliser isGrouped() pour détecter si terrain groupé
        boolean isGrouped = plot.isGrouped();
        int totalSurface = isGrouped ? (plot.getChunks().size() * 256) : 256;

        int line = 15; // Commencer du haut

        // Ligne vide pour l'espacement
        objective.getScore(ChatColor.DARK_GRAY + " ").setScore(line--);

        // SECTION TERRAIN
        objective.getScore(ChatColor.YELLOW + "" + ChatColor.BOLD + "Terrain").setScore(line--);

        // Type de terrain
        objective.getScore(" " + ChatColor.GRAY + "Type: " + ChatColor.WHITE + plot.getType().getDisplayName()).setScore(line--);

        // Surface (sauf pour MUNICIPAL et PUBLIC)
        PlotType plotType = plot.getType();
        boolean shouldShowSurface = (plotType != PlotType.MUNICIPAL && plotType != PlotType.PUBLIC);

        if (shouldShowSurface) {
            if (isGrouped) {
                objective.getScore(" " + ChatColor.GRAY + "Surface: " + ChatColor.WHITE + totalSurface + "m²").setScore(line--);
            } else {
                objective.getScore(" " + ChatColor.GRAY + "Surface: " + ChatColor.WHITE + "256m²").setScore(line--);
            }
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
        boolean forSale = plot.isForSale();
        boolean forRent = plot.isForRent();
        double salePrice = plot.getSalePrice();
        double rentPrice = plot.getRentPricePerDay();
        UUID renterUuid = plot.getRenterUuid();
        int rentDays = plot.getRentDaysRemaining();
        boolean hasEconomyInfo = forSale || forRent || renterUuid != null;

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
     * Supprime le scoreboard d'un joueur (quand il quitte une ville)
     */
    private void removeScoreboard(Player player) {
        // Ne pas supprimer le scoreboard si le joueur a un scoreboard médical actif
        if (plugin.getMedicalSystemManager() != null &&
            plugin.getMedicalSystemManager().hasMedicalScoreboard(player)) {
            return;
        }

        playerScoreboards.remove(player.getUniqueId());
        // Réinitialiser au scoreboard par défaut
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
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

        // Utiliser plot.getChunks() pour récupérer tous les chunks (groupés ou non)
        java.util.Set<String> chunkKeys = new java.util.HashSet<>(plot.getChunks());

        // Créer une tâche répétitive pour afficher les particules en continu
        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                // Arrêter si le joueur est déconnecté
                if (!player.isOnline()) {
                    this.cancel();
                    stopAutoDisplay(playerUuid);
                    return;
                }

                // Vérifier si le joueur est toujours sur le terrain
                Plot currentPlot = claimManager.getPlotAt(player.getLocation().getChunk());
                if (currentPlot == null || !currentPlot.equals(plot)) {
                    // Le joueur a quitté le terrain, arrêter les particules
                    this.cancel();
                    stopAutoDisplay(playerUuid);
                    return;
                }

                // Afficher les particules pour chaque chunk
                for (String chunkKey : chunkKeys) {
                    String[] parts = chunkKey.split(":");
                    if (parts.length == 3) {
                        String worldName = parts[0];
                        int chunkX = Integer.parseInt(parts[1]);
                        int chunkZ = Integer.parseInt(parts[2]);
                        displayPlotBorder(worldName, chunkX, chunkZ, color, chunkKeys);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L).getTaskId(); // Répéter toutes les 0.25 secondes

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
            for (double x = startX; x < startX + 16; x += 0.25) {
                spawnParticleColumn(world, x + 0.5, startZ + 0.5, color);
            }
        }

        // Côté Est
        if (showEast) {
            for (double z = startZ; z < startZ + 16; z += 0.25) {
                spawnParticleColumn(world, startX + 16.0, z + 0.5, color);
            }
        }

        // Côté Sud
        if (showSouth) {
            for (double x = startX; x < startX + 16; x += 0.25) {
                spawnParticleColumn(world, x + 0.5, startZ + 16.0, color);
            }
        }

        // Côté Ouest
        if (showWest) {
            for (double z = startZ; z < startZ + 16; z += 0.25) {
                spawnParticleColumn(world, startX + 0.0, z + 0.5, color);
            }
        }
    }

    /**
     * Spawn une colonne de particules à une position donnée (version réduite en hauteur)
     */
    private void spawnParticleColumn(World world, double x, double z, Particle.DustOptions color) {
        int groundY = world.getHighestBlockYAt((int) x, (int) z);

        // Spawn 1 niveau de particules (seulement le haut visible)
        for (int i = 0; i < 1; i++) {
            double y = groundY + 1.1 + (i * 0.4);

            // Spawn 2 particules pour la densité (réduit pour moins de lag)
            for (int j = 0; j < 2; j++) {
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

}
