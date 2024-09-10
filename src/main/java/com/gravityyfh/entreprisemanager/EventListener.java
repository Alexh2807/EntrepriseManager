package com.gravityyfh.entreprisemanager;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.entity.Player;
import java.util.List;
import java.util.UUID;

public class EventListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin; // Ajout de cette ligne

    // Modification du constructeur pour inclure l'instance du plugin
    public EventListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin; // Ajout de cette ligne
        this.entrepriseLogic = entrepriseLogic;
    }


    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Material blockType = event.getBlock().getType();

        // Récupérer tous les types d'entreprises du joueur
        List<String> typesEntreprises = entrepriseLogic.getTypesEntrepriseDuJoueur(player.getName());

        // Debug: afficher tous les types d'entreprises du joueur
        if (typesEntreprises.isEmpty()) {
            player.sendMessage(ChatColor.RED + "[DEBUG] Le joueur ne possède aucune entreprise.");
        } else {
            player.sendMessage(ChatColor.AQUA + "[DEBUG] Types d'entreprises du joueur: " + String.join(", ", typesEntreprises));
        }

        // Vérifier si le bloc fait partie d'une catégorie liée à une entreprise
        String categorieActivite = entrepriseLogic.getCategorieActivite(blockType);

        // Si aucune catégorie d'activité n'est trouvée, le joueur peut casser librement
        if (categorieActivite == null) {
            player.sendMessage(ChatColor.GREEN + "[DEBUG] Ce bloc n'est pas lié à une entreprise. Il peut être cassé librement.");
            return;
        }

        // Vérifier si le joueur possède une entreprise correspondant à la catégorie d'activité
        if (typesEntreprises.contains(categorieActivite)) {
            player.sendMessage(ChatColor.GREEN + "[DEBUG] Le joueur est autorisé à casser le bloc sans restriction.");
            return; // Le joueur peut casser le bloc sans restriction
        }

        // Si le joueur n'est pas membre d'une entreprise correspondant à la catégorie, on vérifie la limite horaire
        boolean actionAllowed = entrepriseLogic.isActionAllowedForPlayer(blockType, playerUUID);
        if (!actionAllowed) {
            event.setCancelled(true);

            // Afficher les messages d'erreur associés à cette catégorie
            List<String> messagesErreur = plugin.getConfig().getStringList("types-entreprise." + categorieActivite + ".message-erreur");
            for (String message : messagesErreur) {
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
            }
        }
    }


}
