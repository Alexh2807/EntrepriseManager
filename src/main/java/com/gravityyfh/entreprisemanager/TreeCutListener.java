package com.gravityyfh.entreprisemanager;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import pl.norbit.treecuter.api.listeners.TreeCutEvent;

import java.util.Set;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
public class TreeCutListener implements Listener {

    private EntrepriseManagerLogic entrepriseManager;

    // Injectez votre logique d'entreprise dans le listener
    public TreeCutListener(EntrepriseManagerLogic entrepriseManager) {
        this.entrepriseManager = entrepriseManager;
    }

    @EventHandler
    public void onTreeCut(TreeCutEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Set<Block> blocks = event.getBlocks();

        // Vérifiez si le joueur a le droit de couper des arbres dans votre système
        boolean isAllowed = true;

        // Vérifiez pour chaque bloc si l'action est autorisée
        for (Block block : blocks) {
            if (!entrepriseManager.isActionAllowedForPlayer(block.getType(), playerUUID)) {
                isAllowed = false;
                break;
            }
        }

        // Si le joueur n'est pas autorisé à couper l'arbre, annulez l'événement
        if (!isAllowed) {
            event.setCancelled(true);
        }
    }
}
