package com.gravityyfh.roleplaycity.Listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.EntrepriseManagerLogic;
import com.gravityyfh.roleplaycity.util.CoreProtectUtil;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;

public class BlockPlaceListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final RoleplayCity plugin;

    public BlockPlaceListener(RoleplayCity plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }

        Block block = event.getBlockPlaced();
        Material blockType = block.getType();
        String blockTypeName = blockType.name();

        // Check anti-duplication via CoreProtect
        boolean preventRevenue = checkAntiDuplication(player, block, blockType);

        // Check placement restrictions
        boolean isBlocked = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_PLACE", blockTypeName, 1);
        if (isBlocked) {
            event.setCancelled(true);
            return;
        }

        // Register productive action if not blocked by anti-duplication
        if (!preventRevenue) {
            entrepriseLogic.enregistrerActionProductive(player, "BLOCK_PLACE", blockType, 1, block);
        }

        // FIX PERFORMANCES: Enregistrer le bloc dans le cache pour éviter les lookups CoreProtect futurs
        com.gravityyfh.roleplaycity.util.PlayerBlockPlaceCache cache = plugin.getBlockPlaceCache();
        if (cache != null) {
            cache.recordBlockPlace(block, player.getName(), player.getUniqueId());
        }
    }

    private boolean checkAntiDuplication(Player player, Block block, Material blockType) {
        CoreProtectAPI cpAPI = CoreProtectUtil.getAPI();
        if (cpAPI == null) {
            return false;
        }

        int lookupSeconds = plugin.getConfig().getInt("anti-duplication.block-place.lookup-seconds", 30);
        List<String[]> lookup = cpAPI.blockLookup(block, lookupSeconds);

        if (lookup == null || lookup.isEmpty()) {
            return false;
        }

        for (String[] data : lookup) {
            CoreProtectAPI.ParseResult result = cpAPI.parseResult(data);

            // Check if block was recently broken (ActionId 0 = Break)
            if (result.getActionId() == 0 && result.getType() == blockType) {
                if (isSamePlayerOrCompany(player, result.getPlayer())) {
                    player.sendMessage(ChatColor.YELLOW + "[Entreprise] Anti-Duplication: Ce bloc a été récemment cassé et replacé. Aucun revenu pour ce placement.");
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isSamePlayerOrCompany(Player player, String otherPlayerName) {
        if (player.getName().equalsIgnoreCase(otherPlayerName)) {
            return true;
        }

        // FIX MULTI-ENTREPRISES: Vérifier si les deux joueurs partagent au moins une entreprise en commun
        java.util.List<String> playerCompanies = entrepriseLogic.getNomsEntreprisesDuMembre(player.getName());
        java.util.List<String> otherPlayerCompanies = entrepriseLogic.getNomsEntreprisesDuMembre(otherPlayerName);

        if (!playerCompanies.isEmpty() && !otherPlayerCompanies.isEmpty()) {
            // Vérifier s'il y a au moins une entreprise commune
            for (String companyName : playerCompanies) {
                if (otherPlayerCompanies.contains(companyName)) {
                    return true; // Entreprise commune trouvée
                }
            }
        }

        return false;
    }
}