package com.gravityyfh.entreprisemanager;

import org.bukkit.ChatColor;
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
        Material blockType = event.getBlock().getType();
        String playerUUIDString = player.getUniqueId().toString();

        if (!entrepriseLogic.isActionAllowedForPlayer(blockType, UUID.fromString(playerUUIDString))) {
            event.setCancelled(true);

            // Récupérer la catégorie d'activité basée sur le type de bloc
            String categorieActivite = entrepriseLogic.getCategorieActivite(blockType);
            if (categorieActivite != null) {
                // Récupérer le message d'erreur personnalisé du fichier config pour cette catégorie
                List<String> messagesErreur = plugin.getConfig().getStringList("types-entreprise." + categorieActivite + ".message-erreur");
                if (!messagesErreur.isEmpty()) {
                    // Envoyer chaque ligne du message d'erreur personnalisé
                    messagesErreur.forEach(message -> player.sendMessage(ChatColor.translateAlternateColorCodes('&', message)));
                    return; // Sortir de la méthode après l'envoi du message
                }
            }
            // Si aucun message personnalisé n'est configuré ou si la catégorie est inconnue, utiliser un message par défaut
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas autorisé à casser ce bloc. Rejoignez une entreprise appropriée pour avoir des permissions étendues.");
        }
    }
}
