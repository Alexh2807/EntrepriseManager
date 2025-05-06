package com.gravityyfh.entreprisemanager;

import org.bukkit.Bukkit; // Ajout de l'import pour Bukkit
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatListener implements Listener {
    private final EntrepriseManager plugin; // Référence au plugin principal
    private final EntrepriseGUI entrepriseGUI; // Référence à l'interface GUI
    private final Map<UUID, String> attenteMontantRetrait = new HashMap<>();
    private final Map<UUID, String> attenteMontantDepot = new HashMap<>();
    // Nouvelle Map pour gérer l'attente du nouveau nom d'entreprise
    private final Map<UUID, String> enAttenteNouveauNom_AncienNom = new HashMap<>();


    // Constructeur acceptant EntrepriseManager et EntrepriseGUI
    public ChatListener(EntrepriseManager plugin, EntrepriseGUI entrepriseGUI) {
        this.plugin = plugin;
        this.entrepriseGUI = entrepriseGUI;
    }

    public void attendreMontantRetrait(Player player, String entrepriseNom) {
        attenteMontantRetrait.put(player.getUniqueId(), entrepriseNom);
        player.sendMessage(ChatColor.GOLD + "Entrez dans le tchat le montant à retirer, ou écrivez \"annuler\" pour annuler.");
    }

    public void attendreMontantDepot(Player player, String entrepriseNom) {
        attenteMontantDepot.put(player.getUniqueId(), entrepriseNom);
        player.sendMessage(ChatColor.GOLD + "Entrez dans le tchat le montant à déposer, ou écrivez \"annuler\" pour annuler.");
    }

    // Nouvelle méthode pour mettre un joueur en attente de saisie pour un nouveau nom
    public void attendreNouveauNomEntreprise(Player player, String ancienNomEntreprise) {
        enAttenteNouveauNom_AncienNom.put(player.getUniqueId(), ancienNomEntreprise);
        player.sendMessage(ChatColor.GOLD + "Entrez le nouveau nom pour l'entreprise '" + ChatColor.YELLOW + ancienNomEntreprise + ChatColor.GOLD + "' dans le tchat, ou tapez 'annuler'.");
    }


    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String message = event.getMessage(); // Récupérer le message une seule fois

        if (attenteMontantRetrait.containsKey(playerId)) {
            event.setCancelled(true);
            String entrepriseNom = attenteMontantRetrait.get(playerId); // Récupérer avant de remove

            if (message.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.RED + "Retrait annulé.");
                attenteMontantRetrait.remove(playerId);
                // Réouvrir le menu de gestion de l'entreprise spécifique si possible, sinon le menu principal
                EntrepriseManagerLogic.Entreprise entreprise = plugin.getEntrepriseLogic().getEntreprise(entrepriseNom);
                if (entreprise != null && entreprise.getGerant().equalsIgnoreCase(player.getName())) {
                    // Il faut une méthode dans EntrepriseGUI pour ouvrir directement ce menu
                    // entrepriseGUI.openManageSpecificEntrepriseMenu(player, entreprise);
                    // Pour l'instant, retour au menu principal simple
                    entrepriseGUI.openMainMenu(player);
                } else {
                    entrepriseGUI.openMainMenu(player);
                }
                return;
            }

            try {
                double montant = Double.parseDouble(message);
                plugin.getEntrepriseLogic().retirerArgent(player, entrepriseNom, montant); // La méthode gère les messages
                attenteMontantRetrait.remove(playerId);
                // Pas besoin de réouvrir le GUI ici, retirerArgent peut le faire ou le joueur utilise /entreprise gui
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Montant invalide. Veuillez réessayer ou tapez 'annuler'.");
                // Ne pas retirer le joueur de attenteMontantRetrait pour permettre une nouvelle saisie
            }
        } else if (attenteMontantDepot.containsKey(playerId)) {
            event.setCancelled(true);
            String entrepriseNom = attenteMontantDepot.get(playerId);

            if (message.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.RED + "Dépôt annulé.");
                attenteMontantDepot.remove(playerId);
                EntrepriseManagerLogic.Entreprise entreprise = plugin.getEntrepriseLogic().getEntreprise(entrepriseNom);
                if (entreprise != null && (entreprise.getGerant().equalsIgnoreCase(player.getName()) || entreprise.getEmployes().contains(player.getName()))) {
                    // entrepriseGUI.openManageSpecificEntrepriseMenu(player, entreprise); // Ou openViewSpecific
                    entrepriseGUI.openMainMenu(player);
                } else {
                    entrepriseGUI.openMainMenu(player);
                }
                return;
            }

            try {
                double montant = Double.parseDouble(message);
                plugin.getEntrepriseLogic().deposerArgent(player, entrepriseNom, montant);
                attenteMontantDepot.remove(playerId);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Montant invalide. Veuillez réessayer ou tapez 'annuler'.");
            }
        } else if (enAttenteNouveauNom_AncienNom.containsKey(playerId)) {
            event.setCancelled(true);
            String ancienNom = enAttenteNouveauNom_AncienNom.remove(playerId); // Important de retirer pour éviter boucle
            String nouveauNomPropose = message.trim();

            if (nouveauNomPropose.equalsIgnoreCase("annuler")) {
                player.sendMessage(ChatColor.RED + "Renommage annulé.");
                // Optionnel: réouvrir le menu de gestion de l'entreprise
                // EntrepriseManagerLogic.Entreprise entreprise = plugin.getEntrepriseLogic().getEntreprise(ancienNom);
                // if (entreprise != null && entreprise.getGerant().equalsIgnoreCase(player.getName())) {
                //     entrepriseGUI.openManageSpecificEntrepriseMenu(player, entreprise);
                // } else {
                //     entrepriseGUI.openMainMenu(player);
                // }
                return;
            }

            // Vérification de la validité du nom
            if (!nouveauNomPropose.matches("^[a-zA-Z0-9_\\-]+$")) {
                player.sendMessage(ChatColor.RED + "Le nouveau nom contient des caractères invalides. Utilisez uniquement lettres (non accentuées), chiffres, _ et -.");
                enAttenteNouveauNom_AncienNom.put(playerId, ancienNom); // Redemander
                return;
            }

            if (plugin.getEntrepriseLogic().getEntreprise(nouveauNomPropose) != null) {
                player.sendMessage(ChatColor.RED + "Une entreprise avec le nom '" + ChatColor.YELLOW + nouveauNomPropose + ChatColor.RED + "' existe déjà.");
                enAttenteNouveauNom_AncienNom.put(playerId, ancienNom); // Redemander
                return;
            }
            if (nouveauNomPropose.equalsIgnoreCase(ancienNom)){
                player.sendMessage(ChatColor.RED + "Le nouveau nom est identique à l'ancien.");
                enAttenteNouveauNom_AncienNom.put(playerId, ancienNom); // Redemander
                return;
            }


            // Exécuter le renommage dans le thread principal car cela modifie des données du plugin et interagit avec Vault
            Player finalPlayer = player; // Pour utilisation dans la lambda
            String finalAncienNom = ancienNom;
            String finalNouveauNomPropose = nouveauNomPropose;

            Bukkit.getScheduler().runTask(plugin, () -> {
                plugin.getEntrepriseLogic().renameEntreprise(finalPlayer, finalAncienNom, finalNouveauNomPropose);
                // Le message de succès/échec est géré par la méthode renameEntreprise elle-même.
                // Après le renommage, on pourrait vouloir ré-ouvrir le menu de l'entreprise renommée,
                // mais pour l'instant, on laisse le joueur utiliser /entreprise gui.
            });
        }
        // Si aucune condition n'est remplie, le message passe normalement dans le chat.
    }
}