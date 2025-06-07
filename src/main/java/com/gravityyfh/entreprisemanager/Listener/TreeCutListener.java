package com.gravityyfh.entreprisemanager.Listener;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.EntrepriseManagerLogic;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import pl.norbit.treecuter.api.listeners.TreeCutEvent; // Assurez-vous que cet import est correct

import java.util.Set;
import java.util.logging.Level; // Ajout pour les logs

public class TreeCutListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;

    public TreeCutListener(EntrepriseManagerLogic entrepriseLogic, EntrepriseManager plugin) {
        this.entrepriseLogic = entrepriseLogic;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTreeCut(TreeCutEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) {
            plugin.getLogger().log(Level.FINER, "TreeCutEvent ignoré (Joueur en créatif : " + player.getName() + ")");
            return; // Pas de restrictions ou de CA en créatif
        }

        Set<Block> blocksCut = event.getBlocks(); // Blocs qui vont être coupés par l'API TreeCuter

        // Trouver le premier bloc de type LOG ou STEM pour vérifier les restrictions
        Block firstLogBlock = null;
        Material firstLogMaterial = null;
        for (Block block : blocksCut) {
            Material type = block.getType();
            // Détection plus large des types de bois/tiges
            if (type.name().endsWith("_LOG") || type.name().endsWith("_WOOD") || type.name().contains("STEM") || type == Material.MANGROVE_ROOTS || type == Material.MUDDY_MANGROVE_ROOTS) {
                firstLogBlock = block;
                firstLogMaterial = type;
                break;
            }
        }

        if (firstLogBlock == null || firstLogMaterial == null) {
            plugin.getLogger().log(Level.FINER, "TreeCutEvent ignoré (Aucun bloc de type LOG/STEM/WOOD trouvé dans la liste) pour " + player.getName());
            return; // Pas une bûche ? Ou comportement API inattendu. Ignorer.
        }

        String firstLogMaterialName = firstLogMaterial.name(); // Obtenir le nom du matériau en String
        plugin.getLogger().log(Level.INFO, "[DEBUG TreeCut] Événement : " + player.getName() + " coupe un arbre (type principal : " + firstLogMaterialName + ")");


        // Vérifier les restrictions pour le type de bûche principal.
        // Utiliser la surcharge qui prend le nom du matériau (String).
        // On vérifie la restriction pour 1 unité, car la coupe d'arbre est une seule action du point de vue de la limite horaire.
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", firstLogMaterialName, 1);

        plugin.getLogger().log(Level.INFO, "[DEBUG TreeCut] Résultat vérification restriction pour " + player.getName() + " coupant " + firstLogMaterialName + " : " + (isBlocked ? "BLOQUÉ" : "AUTORISÉ"));

        if (isBlocked) {
            event.setCancelled(true); // Action bloquée (limite non-membre atteinte pour ce type de bois)
            plugin.getLogger().log(Level.INFO, "[DEBUG TreeCut] Action bloquée, événement TreeCut annulé pour " + player.getName() + " coupant " + firstLogMaterialName);
            // Le message d'erreur est déjà envoyé par verifierEtGererRestrictionAction.
            return;
        }

        // Si l'action n'est pas bloquée, enregistrer l'action productive pour CHAQUE bloc pertinent coupé.
        int logsCounted = 0;
        for (Block block : blocksCut) {
            Material blockType = block.getType();
            // Enregistrer uniquement les bûches/bois/tiges pour le CA
            if (blockType.name().endsWith("_LOG") || blockType.name().endsWith("_WOOD") || blockType.name().contains("STEM") || blockType == Material.MANGROVE_ROOTS || blockType == Material.MUDDY_MANGROVE_ROOTS) {
                // Utiliser la surcharge de enregistrerActionProductive qui prend le Block en argument.
                plugin.getLogger().log(Level.FINER, "[DEBUG TreeCut] Enregistrement action pour " + player.getName() + " : bûche " + blockType.name());
                entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1, block);
                logsCounted++;
            }
        }
        if (logsCounted > 0) {
            plugin.getLogger().log(Level.INFO, "[DEBUG TreeCut] " + logsCounted + " bûches/tiges enregistrées pour " + player.getName());
        }
    }
}