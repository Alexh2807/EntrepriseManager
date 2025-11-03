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

        // Check CoreProtect history for player-placed blocks
        boolean blockWasPlacedBySamePlayer = false;
        boolean blockWasPlayerPlaced = false;

        CoreProtectAPI cpAPI = CoreProtectUtil.getAPI();
        if (cpAPI != null) {
            int lookupSeconds = plugin.getConfig().getInt("anti-duplication.block-break.history-lookup-seconds", 86400 * 30);
            List<String[]> lookup = cpAPI.blockLookup(block, lookupSeconds);

            if (lookup != null && !lookup.isEmpty()) {
                for (String[] data : lookup) {
                    CoreProtectAPI.ParseResult result = cpAPI.parseResult(data);
                    if (result.getActionId() == 1 && result.getType() == blockType) {
                        blockWasPlayerPlaced = true;
                        if (player.getName().equalsIgnoreCase(result.getPlayer())) {
                            blockWasPlacedBySamePlayer = true;
                            break;
                        }
                    }
                }

                if (blockWasPlayerPlaced && !blockWasPlacedBySamePlayer) {
                    player.sendMessage(ChatColor.YELLOW + "[Entreprise] Ce bloc a été précédemment posé par un joueur. Aucun revenu généré.");
                }
            }
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