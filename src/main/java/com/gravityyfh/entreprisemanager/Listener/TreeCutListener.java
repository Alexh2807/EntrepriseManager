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
import pl.norbit.treecuter.api.listeners.TreeCutEvent;

import java.util.Set;
import java.util.logging.Level;

public class TreeCutListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;

    public TreeCutListener(EntrepriseManagerLogic entrepriseLogic, EntrepriseManager plugin) {
        this.entrepriseLogic = entrepriseLogic;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTreeCut(TreeCutEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Set<Block> blocksCut = event.getBlocks();

        // --- NOUVELLE LOGIQUE ---
        int totalLogsInTree = 0;
        Material firstLogMaterial = null;

        // 1. Compter tous les blocs de bois avant de faire quoi que ce soit
        for (Block block : blocksCut) {
            Material type = block.getType();
            if (type.name().endsWith("_LOG") || type.name().endsWith("_WOOD") || type.name().contains("STEM") || type == Material.MANGROVE_ROOTS || type == Material.MUDDY_MANGROVE_ROOTS) {
                if (firstLogMaterial == null) {
                    firstLogMaterial = type; // On prend le premier type trouvé comme référence pour la restriction
                }
                totalLogsInTree++;
            }
        }

        if (totalLogsInTree == 0 || firstLogMaterial == null) {
            plugin.getLogger().log(Level.FINER, "TreeCutEvent ignoré (Aucun bloc de log trouvé) pour " + player.getName());
            return;
        }

        String firstLogMaterialName = firstLogMaterial.name();
        plugin.getLogger().log(Level.INFO, "[DEBUG TreeCut] Événement : " + player.getName() + " coupe un arbre (" + totalLogsInTree + " logs de type principal : " + firstLogMaterialName + ")");

        // 2. Vérifier la restriction sur la base de la quantité totale de bois
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", firstLogMaterialName, totalLogsInTree);

        plugin.getLogger().log(Level.INFO, "[DEBUG TreeCut] Résultat vérification restriction pour " + player.getName() + " : " + (isBlocked ? "BLOQUÉ" : "AUTORISÉ"));

        if (isBlocked) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.INFO, "[DEBUG TreeCut] Action bloquée, événement TreeCut annulé pour " + player.getName());
            return;
        }
        // --- FIN NOUVELLE LOGIQUE ---

        // 3. Si ce n'est pas bloqué, enregistrer le revenu pour chaque bloc
        int logsRecorded = 0;
        for (Block block : blocksCut) {
            Material blockType = block.getType();
            if (blockType.name().endsWith("_LOG") || blockType.name().endsWith("_WOOD") || blockType.name().contains("STEM") || blockType == Material.MANGROVE_ROOTS || blockType == Material.MUDDY_MANGROVE_ROOTS) {
                plugin.getLogger().log(Level.FINER, "[DEBUG TreeCut] Enregistrement action pour " + player.getName() + " : bûche " + blockType.name());
                entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1, block);
                logsRecorded++;
            }
        }

        if (logsRecorded > 0) {
            plugin.getLogger().log(Level.INFO, "[DEBUG TreeCut] " + logsRecorded + " bûches/tiges enregistrées pour " + player.getName());
        }
    }
}