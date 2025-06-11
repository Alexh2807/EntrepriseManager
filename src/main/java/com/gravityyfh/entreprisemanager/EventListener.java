package com.gravityyfh.entreprisemanager;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable; // <-- IMPORT IMPORTANT
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;

import java.util.List;
import java.util.logging.Level;

public class EventListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;
    private CoreProtectAPI coreProtectAPI = null; // Cache pour l'API CoreProtect

    public EventListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    // Méthode pour obtenir l'API CoreProtect (similaire à BlockPlaceListener)
    private CoreProtectAPI getCoreProtectAPI() {
        if (coreProtectAPI != null && coreProtectAPI.isEnabled()) {
            return coreProtectAPI;
        }
        Plugin cpPlugin = Bukkit.getPluginManager().getPlugin("CoreProtect");

        if (cpPlugin == null || !(cpPlugin instanceof CoreProtect)) {
            plugin.getLogger().warning("[DEBUG CoreProtect] CoreProtect non trouvé ou type incorrect pour EventListener.");
            return null;
        }

        CoreProtectAPI api = ((CoreProtect) cpPlugin).getAPI();
        if (!api.isEnabled()) {
            plugin.getLogger().warning("[DEBUG CoreProtect] API CoreProtect non activée pour EventListener.");
            return null;
        }
        // La documentation v10 de l'API CoreProtect suggère une vérification < 9 pour les fonctionnalités de base.
        if (api.APIVersion() < 9) {
            plugin.getLogger().warning("[DEBUG CoreProtect] Version API CoreProtect < 9. Fonctionnalités anti-duplication peuvent être limitées.");
            return null;
        }
        this.coreProtectAPI = api;
        plugin.getLogger().info("[DEBUG CoreProtect] API CoreProtect chargée avec succès pour EventListener.");
        return api;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Material blockType = block.getType();
        String blockTypeName = blockType.name();

        if (player.getGameMode() == GameMode.CREATIVE) {
            return; // Ignorer le mode créatif
        }

        // --- NOUVELLE LOGIQUE POUR LES CULTURES ---
        if (block.getBlockData() instanceof Ageable) {
            Ageable ageable = (Ageable) block.getBlockData();
            if (ageable.getAge() == ageable.getMaximumAge()) {
                boolean isBlockedByRestriction = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", blockTypeName, 1);
                if (isBlockedByRestriction) {
                    // MODIFICATION : Au lieu d'annuler l'événement, on empêche les drops.
                    event.setDropItems(false);
                    return;
                }
                entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1, block);
                return;
            }
        }
        // --- FIN DE LA NOUVELLE LOGIQUE ---


        // --- LOGIQUE EXISTANTE POUR LES BLOCS NON-CULTIVABLES (MINERAIS, etc.) ---
        CoreProtectAPI cpAPI = getCoreProtectAPI();
        boolean blockWasPlayerPlacedByCoreProtect = false;
        boolean blockWasPlacedBySamePlayer = false;

        if (cpAPI != null) {
            int tempsLookupHistoriqueSecondes = plugin.getConfig().getInt("anti-duplication.block-break.history-lookup-seconds", 86400 * 30);
            List<String[]> resultatLookup = cpAPI.blockLookup(block, tempsLookupHistoriqueSecondes);

            if (resultatLookup != null && !resultatLookup.isEmpty()) {
                for (String[] donneesResultat : resultatLookup) {
                    CoreProtectAPI.ParseResult resultatParse = cpAPI.parseResult(donneesResultat);
                    if (resultatParse.getActionId() == 1 && resultatParse.getType() == blockType) {
                        blockWasPlayerPlacedByCoreProtect = true;
                        if (player.getName().equalsIgnoreCase(resultatParse.getPlayer())) {
                            blockWasPlacedBySamePlayer = true;
                            break;
                        }
                    }
                }
                if (blockWasPlayerPlacedByCoreProtect && !blockWasPlacedBySamePlayer) {
                    player.sendMessage(ChatColor.YELLOW + "[Entreprise] Ce bloc a été précédemment posé par un joueur. Aucun revenu généré.");
                }
            }
        }

        boolean isBlockedByRestriction = false;
        if (!blockWasPlacedBySamePlayer) {
            isBlockedByRestriction = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", blockTypeName, 1);
        }
        if (isBlockedByRestriction) {
            // MODIFICATION : Au lieu d'annuler l'événement, on empêche les drops.
            event.setDropItems(false);
            return;
        }

        if (blockWasPlayerPlacedByCoreProtect) {
            plugin.getLogger().log(Level.INFO, "[DEBUG Break] Action productive SAUTÉE (bloc posé par joueur).");
        } else {
            entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1, block);
        }
    }
}