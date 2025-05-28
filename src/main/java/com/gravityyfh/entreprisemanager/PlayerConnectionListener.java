package com.gravityyfh.entreprisemanager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.logging.Level; // Pour un log plus précis

public class PlayerConnectionListener implements Listener {

    private final EntrepriseManager plugin; // Référence au plugin principal pour le scheduler
    private final EntrepriseManagerLogic entrepriseLogic;

    /**
     * Constructeur pour le listener de connexion des joueurs.
     * @param plugin L'instance principale du plugin EntrepriseManager.
     * @param entrepriseLogic L'instance de la logique métier du plugin.
     */
    public PlayerConnectionListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        if (plugin == null) {
            throw new IllegalArgumentException("L'instance du plugin ne peut pas être nulle pour PlayerConnectionListener !");
        }
        if (entrepriseLogic == null) {
            throw new IllegalArgumentException("L'instance de EntrepriseManagerLogic ne peut pas être nulle pour PlayerConnectionListener !");
        }
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Planifier l'envoi des messages différés après un court délai
        // pour s'assurer que le joueur est complètement initialisé sur le serveur
        // et que les autres plugins ont eu le temps de charger ses données si nécessaire.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Vérifier à nouveau si le joueur est toujours en ligne,
            // au cas où il se déconnecterait très rapidement après sa connexion.
            if (player.isOnline()) {
                plugin.getLogger().log(Level.FINER, "Envoi des messages différés pour " + player.getName() + " après connexion.");
                entrepriseLogic.envoyerPrimesDifferreesEmployes(player);
                entrepriseLogic.envoyerPrimesDifferreesGerants(player);
                // Vous pourriez ajouter ici l'envoi d'autres types de notifications différées si nécessaire.
            }
        }, 20L * 5); // Délai de 5 secondes (20 ticks/seconde * 5 secondes)
    }

    // Vous pourriez ajouter ici un @EventHandler pour PlayerQuitEvent si vous avez besoin de
    // gérer des logiques spécifiques à la déconnexion (par exemple, terminer des sessions d'activité
    // si ce n'est pas déjà géré ailleurs, ou sauvegarder des données spécifiques au joueur).
    // Pour l'instant, la logique de fin de session d'activité semble être gérée par le activityCheckTask
    // dans EntrepriseManagerLogic.
}