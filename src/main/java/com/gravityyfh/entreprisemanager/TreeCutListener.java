package com.gravityyfh.entreprisemanager;

import org.bukkit.ChatColor;
import org.bukkit.GameMode; // Ajout pour vérifier le GameMode
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import pl.norbit.treecuter.api.listeners.TreeCutEvent; // Assurez-vous que cet import est correct pour votre API TreeCuter

import java.util.List; // Pour les messages d'erreur
import java.util.Set;
// UUID n'est plus directement utilisé car on passe l'objet Player

public class TreeCutListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin; // Référence au plugin principal pour accéder à la config

    public TreeCutListener(EntrepriseManagerLogic entrepriseLogic, EntrepriseManager plugin) {
        this.entrepriseLogic = entrepriseLogic;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTreeCut(TreeCutEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) {
            return; // Pas de restrictions ou de CA en créatif
        }

        Set<Block> blocksCut = event.getBlocks(); // Blocs qui vont être coupés par l'API TreeCuter

        // Il faut vérifier la restriction sur le premier bloc de type LOG pour déterminer si l'action globale est permise.
        // L'API TreeCuter gère la coupe de l'arbre entier. Si la première action est bloquée, on annule tout.
        Material firstLogType = null;
        for (Block block : blocksCut) {
            if (block.getType().name().endsWith("_LOG") || block.getType().name().endsWith("_WOOD") || block.getType().name().contains("STEM")) { // Détecter les bûches/tiges
                firstLogType = block.getType();
                break;
            }
        }

        if (firstLogType == null) {
            // Pas une bûche ? Ou l'API a un comportement inattendu. On ignore pour éviter des erreurs.
            return;
        }

        // Idée 3: Vérifier les restrictions pour le type de bûche principal
        // On considère la coupe de l'arbre comme UNE action pour la limite non-membre. Quantité = 1 pour la vérification.
        if (entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", firstLogType, 1)) {
            event.setCancelled(true); // Action bloquée (limite non-membre atteinte pour ce type de bois)
            // Le message d'erreur est déjà envoyé par verifierEtGererRestrictionAction.
            // On peut ajouter un message spécifique à TreeCut si besoin.
            // player.sendMessage(ChatColor.RED + "Vous avez atteint votre limite de coupe pour ce type de bois cette heure.");
            return;
        }

        // Si l'action n'est pas bloquée, alors Idées 1 & 4: Enregistrer l'action productive pour CHAQUE bûche
        // Cela suppose que l'API TreeCuter ne cancelle pas l'événement si on ne le fait pas ici.
        // Et que l'événement est bien déclenché AVANT que les blocs ne soient réellement cassés par l'API.
        int logsCounted = 0;
        for (Block block : blocksCut) {
            Material blockType = block.getType();
            // Enregistrer uniquement les bûches/bois pour le CA (pas les feuilles, etc.)
            if (blockType.name().endsWith("_LOG") || blockType.name().endsWith("_WOOD") || blockType.name().contains("STEM")) {
                entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1);
                logsCounted++;
            }
        }
        if (logsCounted > 0) {
            // player.sendMessage(ChatColor.DARK_AQUA + "[DEBUG] TreeCut: " + logsCounted + " bûches enregistrées pour le CA."); // Debug
        }
    }
}