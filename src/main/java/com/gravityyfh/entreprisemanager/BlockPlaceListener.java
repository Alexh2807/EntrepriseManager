package com.gravityyfh.entreprisemanager;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockPlaceListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;

    public BlockPlaceListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == GameMode.CREATIVE) {
            // En mode créatif, aucune restriction ni enregistrement de productivité.
            return;
        }

        Material blockType = event.getBlockPlaced().getType();
        // La quantité pour BlockPlace est toujours 1.

        // Idée 3: Vérifier les restrictions
        if (entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_PLACE", blockType, 1)) {
            event.setCancelled(true); // Action bloquée par la restriction (limite non-membre atteinte)
            // Le message d'erreur est déjà envoyé par verifierEtGererRestrictionAction
            // player.sendMessage(ChatColor.DARK_RED + "[DEBUG] Placement de " + blockType.name() + " annulé par restriction."); // Debug
            return;
        }

        // Si l'action n'est pas bloquée, alors Idées 1 & 4: Enregistrer l'action productive
        entrepriseLogic.enregistrerActionProductive(player, "BLOCK_PLACE", blockType, 1);
        // player.sendMessage(ChatColor.DARK_AQUA + "[DEBUG] Action BLOCK_PLACE enregistrée pour " + blockType.name()); // Debug
    }
}