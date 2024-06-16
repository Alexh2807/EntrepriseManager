package com.gravityyfh.entreprisemanager;

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
    private Map<UUID, String> attenteMontantRetrait = new HashMap<>();
    private Map<UUID, String> attenteMontantDepot = new HashMap<>();

    // Constructeur acceptant EntrepriseManager
    public ChatListener(EntrepriseManager plugin) {
        this.plugin = plugin;
    }

    public void attendreMontantRetrait(Player player, String entrepriseNom) {
        attenteMontantRetrait.put(player.getUniqueId(), entrepriseNom);
        player.sendMessage(ChatColor.GOLD + "Entrez dans le tchat le montant à retirer, ou écrivez \"cancel\" pour annuler.");
    }

    public void attendreMontantDepot(Player player, String entrepriseNom) {
        attenteMontantDepot.put(player.getUniqueId(), entrepriseNom);
        player.sendMessage(ChatColor.GOLD + "Entrez dans le tchat le montant à déposer, ou écrivez \"cancel\" pour annuler.");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (attenteMontantRetrait.containsKey(playerId)) {
            event.setCancelled(true); // Ne pas envoyer le message dans le chat global
            String message = event.getMessage();

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.RED + "Retrait annulé.");
                attenteMontantRetrait.remove(playerId);
                return;
            }

            try {
                double montant = Double.parseDouble(message);
                // Utilisation de la référence au plugin pour accéder à EntrepriseManagerLogic
                plugin.getEntrepriseLogic().retirerArgent(player, attenteMontantRetrait.get(playerId), montant);
                attenteMontantRetrait.remove(playerId);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Montant invalide. Veuillez réessayer.");
                // Ne pas retirer le joueur de attenteMontantRetrait pour permettre une nouvelle saisie
            }
        } else if (attenteMontantDepot.containsKey(playerId)) {
            event.setCancelled(true); // Ne pas envoyer le message dans le chat global
            String message = event.getMessage();

            if (message.equalsIgnoreCase("cancel")) {
                player.sendMessage(ChatColor.RED + "Dépôt annulé.");
                attenteMontantDepot.remove(playerId);
                return;
            }

            try {
                double montant = Double.parseDouble(message);
                // Utilisation de la référence au plugin pour accéder à EntrepriseManagerLogic
                plugin.getEntrepriseLogic().deposerArgent(player, attenteMontantDepot.get(playerId), montant);
                attenteMontantDepot.remove(playerId);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Montant invalide. Veuillez réessayer.");
                // Ne pas retirer le joueur de attenteMontantDepot pour permettre une nouvelle saisie
            }
        }
    }
}
