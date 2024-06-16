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

public class TreeCutListener implements Listener {
    private EntrepriseManagerLogic entrepriseLogic; // Assurez-vous de l'initialiser correctement

    public TreeCutListener(EntrepriseManagerLogic entrepriseLogic) {
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler
    public void onTreeCut(TreeCutEvent e) {
        Player player = e.getPlayer();
        UUID playerUUID = player.getUniqueId();
        Set<Block> blocks = e.getBlocks();

        // Vérifier chaque bloc pour voir s'il est dans la liste autorisée
        for (Block block : blocks) {
            Material blockType = block.getType();
            if (!isActionAllowedForPlayer(blockType, playerUUID)) {
                e.setCancelled(true);
                break; // Pas besoin de vérifier les autres blocs une fois qu'un bloc est refusé
            }
        }
    }

    private boolean isActionAllowedForPlayer(Material blockType, UUID playerUUID) {
        String typeEntreprise = entrepriseLogic.getTypeEntrepriseDuJoueur(playerUUID.toString());
        // Ici, utilisez getCategorieActivite de votre EntrepriseManagerLogic si elle fait déjà ce travail
        String categorieActivite = entrepriseLogic.getCategorieActivite(blockType);

        if (categorieActivite != null && categorieActivite.equals("Deforestation")) {
            if (typeEntreprise != null && typeEntreprise.equals(categorieActivite)) {
                return true; // Le joueur peut couper car il appartient à une entreprise de déforestation
            }

            // Vérifier la limite pour les non-membres si le joueur n'appartient à aucune entreprise ou n'a pas les permissions
            return entrepriseLogic.checkDailyLimitForNonMembers(playerUUID, categorieActivite);
        }

        // Si la catégorie d'activité n'est pas déforestation ou n'est pas déterminée, autoriser par défaut
        return true;
    }
}
