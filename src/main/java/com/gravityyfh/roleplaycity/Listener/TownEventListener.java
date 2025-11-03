package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.event.TownDeleteEvent;
import com.gravityyfh.roleplaycity.town.event.TownMemberLeaveEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Listener pour les événements du système de ville intégré
 * Remplace TownyListener pour gérer les entreprises lors de suppressions de villes
 */
public class TownEventListener implements Listener {

    private final RoleplayCity plugin;
    private final EntrepriseManagerLogic entrepriseLogic;

    public TownEventListener(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    /**
     * Gère la suppression d'une ville.
     * Si une ville est supprimée, toutes les entreprises et leurs boutiques situées dans cette ville sont également supprimées.
     */
    @EventHandler
    public void onTownDelete(TownDeleteEvent event) {
        String deletedTownName = event.getTownName();

        if (deletedTownName == null || deletedTownName.isEmpty()) {
            plugin.getLogger().warning("TownDeleteEvent reçu, mais impossible de récupérer le nom de la ville !");
            return;
        }

        plugin.getLogger().log(Level.INFO, "La ville '" + deletedTownName + "' est en cours de suppression. Nettoyage des entreprises et boutiques associées...");

        // 1. Supprimer toutes les boutiques de la ville
        if (plugin.getShopManager() != null) {
            plugin.getShopManager().deleteShopsInTown(deletedTownName);
        }

        // 2. Supprimer toutes les entreprises de la ville
        List<EntrepriseManagerLogic.Entreprise> entreprisesAConsiderer = new ArrayList<>(entrepriseLogic.getEntreprises());
        int entreprisesSupprimees = 0;
        for (EntrepriseManagerLogic.Entreprise entreprise : entreprisesAConsiderer) {
            if (deletedTownName.equalsIgnoreCase(entreprise.getVille())) {
                entrepriseLogic.handleEntrepriseRemoval(entreprise, "La ville '" + deletedTownName + "' a été supprimée.");
                entreprisesSupprimees++;
            }
        }

        if (entreprisesSupprimees > 0) {
            plugin.getLogger().log(Level.INFO, entreprisesSupprimees + " entreprise(s) supprimée(s) suite à la suppression de la ville '" + deletedTownName + "'.");
        } else {
            plugin.getLogger().log(Level.INFO, "Aucune entreprise n'était associée à la ville supprimée '" + deletedTownName + "'.");
        }
    }

    /**
     * Gère le départ d'un membre d'une ville.
     * Si le membre qui quitte la ville est le gérant d'une entreprise située dans cette même ville,
     * l'entreprise est supprimée.
     */
    @EventHandler
    public void onMemberLeaveTown(TownMemberLeaveEvent event) {
        String playerName = event.getPlayerName();
        String townName = event.getTownName();
        java.util.UUID playerUuid = event.getPlayerUuid();

        if (playerName == null || townName == null || playerUuid == null) {
            plugin.getLogger().warning("TownMemberLeaveEvent reçu, mais joueur ou ville manquant.");
            return;
        }

        plugin.getLogger().log(Level.INFO, "Le membre '" + playerName + "' quitte/est retiré de la ville '" + townName + "'. Vérification des entreprises gérées...");

        // Copie de la liste pour éviter ConcurrentModificationException
        List<EntrepriseManagerLogic.Entreprise> entreprisesAConsiderer = new ArrayList<>(entrepriseLogic.getEntreprises());
        int entreprisesSupprimees = 0;

        for (EntrepriseManagerLogic.Entreprise entreprise : entreprisesAConsiderer) {
            // Vérifier si le membre est le gérant de cette entreprise ET si l'entreprise est bien dans la ville qu'il quitte
            if (entreprise.getGerant().equalsIgnoreCase(playerName) && entreprise.getVille().equalsIgnoreCase(townName)) {
                plugin.getLogger().log(Level.INFO, "Le gérant '" + playerName + "' de l'entreprise '" + entreprise.getNom() + "' a quitté sa ville d'attache ('" + townName + "'). Suppression de l'entreprise.");
                entrepriseLogic.handleEntrepriseRemoval(entreprise, "Le gérant '" + playerName + "' a quitté la ville '" + townName + "'.");
                entreprisesSupprimees++;
            }
        }

        if (entreprisesSupprimees > 0) {
            plugin.getLogger().log(Level.INFO, entreprisesSupprimees + " entreprise(s) gérée(s) par '" + playerName + "' dans la ville '" + townName + "' ont été supprimée(s) suite à son départ.");
        } else {
            plugin.getLogger().log(Level.INFO, "Le départ de '" + playerName + "' de la ville '" + townName + "' n'a affecté aucune entreprise qu'il gérait dans cette ville.");
        }

        // === NOUVEAU : Gestion des terrains du joueur qui quitte ===
        if (plugin.getTownManager() != null && plugin.getCompanyPlotManager() != null) {
            com.gravityyfh.roleplaycity.town.data.Town town = plugin.getTownManager().getTown(townName);
            if (town == null) {
                return;
            }

            // Trouver tous les terrains appartenant au joueur
            List<com.gravityyfh.roleplaycity.town.data.Plot> playerPlots = new ArrayList<>();
            for (com.gravityyfh.roleplaycity.town.data.Plot plot : town.getPlots().values()) {
                if (playerUuid.equals(plot.getOwnerUuid())) {
                    playerPlots.add(plot);
                }
            }

            if (!playerPlots.isEmpty()) {
                int particulierSold = 0;
                int professionnelKept = 0;
                int professionnelSold = 0;

                for (com.gravityyfh.roleplaycity.town.data.Plot plot : playerPlots) {
                    if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PARTICULIER) {
                        // Terrain PARTICULIER → Vente automatique
                        plugin.getTownManager().transferPlotToTown(plot, "Propriétaire a quitté la ville");
                        particulierSold++;
                        plugin.getLogger().info(String.format(
                            "[TownEventListener] Terrain PARTICULIER %s:%d,%d vendu (propriétaire %s a quitté)",
                            plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), playerName
                        ));
                    } else if (plot.getType() == com.gravityyfh.roleplaycity.town.data.PlotType.PROFESSIONNEL) {
                        // Terrain PROFESSIONNEL → Vérifier si l'entreprise existe toujours
                        String companySiret = plot.getCompanySiret();
                        if (companySiret != null) {
                            EntrepriseManagerLogic.Entreprise company = plugin.getCompanyPlotManager().getCompanyBySiret(companySiret);
                            if (company != null) {
                                // Entreprise existe → Garder le terrain
                                professionnelKept++;
                                plugin.getLogger().info(String.format(
                                    "[TownEventListener] Terrain PROFESSIONNEL %s:%d,%d conservé (entreprise %s existe)",
                                    plot.getWorldName(), plot.getChunkX(), plot.getChunkZ(), company.getNom()
                                ));
                            } else {
                                // Entreprise n'existe plus → Vendre
                                plugin.getTownManager().transferPlotToTown(plot, "Entreprise propriétaire supprimée");
                                professionnelSold++;
                                plugin.getLogger().info(String.format(
                                    "[TownEventListener] Terrain PROFESSIONNEL %s:%d,%d vendu (entreprise disparue)",
                                    plot.getWorldName(), plot.getChunkX(), plot.getChunkZ()
                                ));
                            }
                        } else {
                            // Pas de SIRET → Vendre
                            plugin.getTownManager().transferPlotToTown(plot, "Terrain PROFESSIONNEL sans entreprise");
                            professionnelSold++;
                        }
                    }
                }

                plugin.getLogger().log(Level.INFO, String.format(
                    "[TownEventListener] Bilan terrains de %s: %d PARTICULIER vendus, %d PROFESSIONNEL conservés, %d PROFESSIONNEL vendus",
                    playerName, particulierSold, professionnelKept, professionnelSold
                ));
            }
        }
        // === FIN DU NOUVEAU CODE ===
    }
}
