package com.gravityyfh.entreprisemanager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
// Utilise l'événement DeleteTownEvent
import com.palmergames.bukkit.towny.event.DeleteTownEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class TownyListener implements Listener {

    private final EntrepriseManager plugin;
    private final EntrepriseManagerLogic entrepriseLogic;

    public TownyListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler
    public void onTownDelete(DeleteTownEvent event) { // Écoute DeleteTownEvent

        // Utilise getTownName() qui est maintenant reconnu
        String deletedTownName = event.getTownName();

        if (deletedTownName == null || deletedTownName.isEmpty()) {
            plugin.getLogger().warning("DeleteTownEvent reçu, mais impossible de récupérer le nom de la ville !");
            return;
        }

        plugin.getLogger().log(Level.INFO, "La ville '" + deletedTownName + "' est en cours de suppression (via DeleteTownEvent). Vérification des entreprises associées...");

        List<EntrepriseManagerLogic.Entreprise> entreprisesAConsiderer = new ArrayList<>(entrepriseLogic.getEntreprises());

        int entreprisesSupprimees = 0;
        for (EntrepriseManagerLogic.Entreprise entreprise : entreprisesAConsiderer) {
            if (entreprise.getVille().equalsIgnoreCase(deletedTownName)) {
                plugin.getLogger().log(Level.INFO, "Suppression de l'entreprise '" + entreprise.getNom() + "' car sa ville '" + deletedTownName + "' a été supprimée.");
                // Appelle la méthode centrale qui gère aussi la vérification des shops
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
}