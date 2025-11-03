package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.palmergames.bukkit.towny.event.DeleteTownEvent;
import com.palmergames.bukkit.towny.event.TownRemoveResidentEvent; // Ajout de l'événement pour un résident qui quitte une ville
import com.palmergames.bukkit.towny.object.Resident;
import com.palmergames.bukkit.towny.object.Town;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class TownyListener implements Listener {

    private final RoleplayCity plugin;
    private final EntrepriseManagerLogic entrepriseLogic;

    public TownyListener(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    /**
     * Gère la suppression d'une ville Towny.
     * Si une ville est supprimée, toutes les entreprises et leurs boutiques situées dans cette ville sont également supprimées.
     * @param event L'événement de suppression de ville.
     */
    @EventHandler
    public void onTownDelete(DeleteTownEvent event) {
        String deletedTownName = event.getTownName();

        if (deletedTownName == null || deletedTownName.isEmpty()) {
            plugin.getLogger().warning("DeleteTownEvent reçu, mais impossible de récupérer le nom de la ville !");
            return;
        }

        plugin.getLogger().log(Level.INFO, "La ville '" + deletedTownName + "' est en cours de suppression. Nettoyage des entreprises et boutiques associées...");

        // --- LOGIQUE MODIFIÉE ET OPTIMISÉE ---

        // 1. Supprimer toutes les boutiques de la ville
        if (plugin.getShopManager() != null) {
            plugin.getShopManager().deleteShopsInTown(deletedTownName);
        }

        // 2. Supprimer toutes les entreprises de la ville
        // On itère sur une copie pour éviter les erreurs de modification concurrente
        List<EntrepriseManagerLogic.Entreprise> entreprisesAConsiderer = new ArrayList<>(entrepriseLogic.getEntreprises());
        int entreprisesSupprimees = 0;
        for (EntrepriseManagerLogic.Entreprise entreprise : entreprisesAConsiderer) {
            if (deletedTownName.equalsIgnoreCase(entreprise.getVille())) {
                // La méthode handleEntrepriseRemoval s'occupe de tout le processus de suppression de l'entreprise
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
     * Gère le départ d'un résident d'une ville Towny.
     * Si le résident qui quitte la ville est le gérant d'une entreprise située dans cette même ville,
     * l'entreprise est supprimée.
     * @param event L'événement de suppression d'un résident d'une ville.
     */
    @EventHandler
    public void onResidentLeaveTown(TownRemoveResidentEvent event) {
        Resident resident = event.getResident();
        Town town = event.getTown();

        if (resident == null || town == null) {
            plugin.getLogger().warning("TownRemoveResidentEvent reçu, mais résident ou ville manquant.");
            return;
        }

        String residentName = resident.getName();
        String townName = town.getName();

        plugin.getLogger().log(Level.INFO, "Le résident '" + residentName + "' quitte/est retiré de la ville '" + townName + "'. Vérification des entreprises gérées...");

        // Copie de la liste pour éviter ConcurrentModificationException
        List<EntrepriseManagerLogic.Entreprise> entreprisesAConsiderer = new ArrayList<>(entrepriseLogic.getEntreprises());
        int entreprisesSupprimees = 0;

        for (EntrepriseManagerLogic.Entreprise entreprise : entreprisesAConsiderer) {
            // Vérifier si le résident est le gérant de cette entreprise
            // ET si l'entreprise est bien dans la ville que le résident quitte
            if (entreprise.getGerant().equalsIgnoreCase(residentName) && entreprise.getVille().equalsIgnoreCase(townName)) {
                plugin.getLogger().log(Level.INFO, "Le gérant '" + residentName + "' de l'entreprise '" + entreprise.getNom() + "' a quitté sa ville d'attache ('" + townName + "'). Suppression de l'entreprise.");
                entrepriseLogic.handleEntrepriseRemoval(entreprise, "Le gérant '" + residentName + "' a quitté la ville '" + townName + "'.");
                entreprisesSupprimees++;
            }
        }

        if (entreprisesSupprimees > 0) {
            plugin.getLogger().log(Level.INFO, entreprisesSupprimees + " entreprise(s) gérée(s) par '" + residentName + "' dans la ville '" + townName + "' ont été supprimée(s) suite à son départ.");
        } else {
            plugin.getLogger().log(Level.INFO, "Le départ de '" + residentName + "' de la ville '" + townName + "' n'a affecté aucune entreprise qu'il gérait dans cette ville.");
        }
    }
}