package com.gravityyfh.entreprisemanager.Listener;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.EntrepriseManagerLogic;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import pl.norbit.treecuter.api.listeners.TreeCutEvent;

import java.util.List;
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

        int totalLogsInTree = 0;
        Material firstLogMaterial = null;

        for (Block block : blocksCut) {
            Material type = block.getType();
            if (type.name().endsWith("_LOG") || type.name().endsWith("_WOOD") || type.name().contains("STEM") || type == Material.MANGROVE_ROOTS || type == Material.MUDDY_MANGROVE_ROOTS) {
                if (firstLogMaterial == null) {
                    firstLogMaterial = type;
                }
                totalLogsInTree++;
            }
        }

        if (totalLogsInTree == 0 || firstLogMaterial == null) {
            return;
        }

        String firstLogMaterialName = firstLogMaterial.name();
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", firstLogMaterialName, totalLogsInTree);

        if (isBlocked) {
            // MODIFICATION : On annule l'événement TreeCuter pour empêcher l'abattage en chaîne,
            // mais cela peut encore causer l'erreur pour le premier bloc.
            // La solution idéale dépend de comment l'API TreeCuter fonctionne.
            // Si l'erreur persiste pour le premier bloc, la logique de EventListener.java devrait la gérer.
            event.setCancelled(true);
            // On envoie aussi un message clair au joueur
            player.sendMessage(ChatColor.RED + "Vous avez atteint votre limite horaire pour l'abattage de ce type de bois.");
            return;
        }

        // Si ce n'est pas bloqué, enregistrer le revenu pour chaque bloc
        int logsRecorded = 0;
        for (Block block : blocksCut) {
            Material blockType = block.getType();
            if (blockType.name().endsWith("_LOG") || blockType.name().endsWith("_WOOD") || blockType.name().contains("STEM") || blockType == Material.MANGROVE_ROOTS || blockType == Material.MUDDY_MANGROVE_ROOTS) {
                entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1, block);
                logsRecorded++;
            }
        }
        plugin.getLogger().log(Level.INFO, "[DEBUG TreeCut] " + logsRecorded + " bûches/tiges enregistrées pour " + player.getName());
    }
}