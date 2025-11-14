package com.gravityyfh.roleplaycity;

import com.gravityyfh.roleplaycity.util.CoreProtectUtil;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.List;

public class EventListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final RoleplayCity plugin;

    public EventListener(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Block block = event.getBlock();
        Material blockType = block.getType();
        String blockTypeName = blockType.name();

        // Handle mature crops
        if (handleAgeable(event, player, block, blockType, blockTypeName)) {
            return;
        }

        // FIX PERFORMANCES: Utiliser le cache au lieu de CoreProtect (500x plus rapide)
        // Ancienne méthode: lookup CoreProtect synchrone = 50-200ms de lag par bloc
        // Nouvelle méthode: cache en mémoire = <1ms
        boolean blockWasPlacedBySamePlayer = false;
        boolean blockWasPlayerPlaced = false;

        com.gravityyfh.roleplaycity.util.PlayerBlockPlaceCache cache = plugin.getBlockPlaceCache();
        if (cache != null) {
            // Vérifier si le bloc a été placé par CE joueur
            blockWasPlacedBySamePlayer = cache.wasPlacedByPlayer(block, player.getName());

            // Vérifier si le bloc a été placé par N'IMPORTE QUEL joueur
            if (!blockWasPlacedBySamePlayer) {
                blockWasPlayerPlaced = cache.wasPlacedByAnyPlayer(block);

                if (blockWasPlayerPlaced) {
                    player.sendMessage(ChatColor.YELLOW + "[Entreprise] Ce bloc a été précédemment posé par un joueur. Aucun revenu généré.");
                }
            }

            // Supprimer le bloc du cache après l'avoir cassé
            cache.removeBlock(block);
        }

        // Check restrictions
        if (!blockWasPlacedBySamePlayer) {
            boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", blockTypeName, 1);
            if (isBlocked) {
                event.setDropItems(false);
                return;
            }
        }

        // Register productive action if block was not player-placed
        if (!blockWasPlayerPlaced) {
            entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1, block);
        }
    }

    private boolean handleAgeable(BlockBreakEvent event, Player player, Block block, Material blockType, String blockTypeName) {
        if (!(block.getBlockData() instanceof Ageable)) {
            return false;
        }

        Ageable ageable = (Ageable) block.getBlockData();
        if (ageable.getAge() != ageable.getMaximumAge()) {
            return false;
        }

        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", blockTypeName, 1);
        if (isBlocked) {
            event.setDropItems(false);
            return true;
        }

        entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1, block);
        return true;
    }
}