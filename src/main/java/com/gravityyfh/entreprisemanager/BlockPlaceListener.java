package com.gravityyfh.entreprisemanager;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;

import java.util.List;
import java.util.logging.Level;

public class BlockPlaceListener implements Listener {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseManager plugin;
    private CoreProtectAPI coreProtectAPI = null;

    public BlockPlaceListener(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
    }

    private CoreProtectAPI getCoreProtectAPI() {
        if (coreProtectAPI != null && coreProtectAPI.isEnabled()) {
            return coreProtectAPI;
        }
        Plugin cpPlugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (cpPlugin == null || !(cpPlugin instanceof CoreProtect)) {
            plugin.getLogger().warning("[DEBUG CoreProtect] CoreProtect non trouvé ou type incorrect pour BlockPlaceListener.");
            return null;
        }
        CoreProtectAPI api = ((CoreProtect) cpPlugin).getAPI();
        if (!api.isEnabled()) {
            plugin.getLogger().warning("[DEBUG CoreProtect] API CoreProtect non activée pour BlockPlaceListener.");
            return null;
        }
        if (api.APIVersion() < 9) {
            plugin.getLogger().warning("[DEBUG CoreProtect] Version API CoreProtect < 9. Certaines fonctionnalités peuvent être limitées.");
            return null;
        }
        this.coreProtectAPI = api;
        plugin.getLogger().info("[DEBUG CoreProtect] API CoreProtect chargée avec succès pour BlockPlaceListener.");
        return api;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) // Priorité haute pour intervenir tôt
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block blockPlaced = event.getBlockPlaced();
        Material blockType = blockPlaced.getType();
        String blockTypeName = blockType.name();
        Location blockLocation = blockPlaced.getLocation();

        if (player.getGameMode() == GameMode.CREATIVE) {
            plugin.getLogger().log(Level.FINER, "[DEBUG Place] Ignoré (Créatif): " + player.getName() + " plaçant " + blockTypeName);
            return;
        }

        plugin.getLogger().log(Level.INFO, "[DEBUG Place] Début: " + player.getName() + " plaçant " + blockTypeName + " à " + blockLocation.toString());

        // Scénario: Le joueur pose un bloc à l'endroit où il (ou son entreprise)
        // l'a déjà précédemment cassé quelques secondes avant (pour éviter "pose-casse-repose" abusif)
        CoreProtectAPI cpAPI = getCoreProtectAPI();
        boolean preventRevenueForPlace = false;

        if (cpAPI != null) {
            int tempsVerificationSecondes = plugin.getConfig().getInt("anti-duplication.block-place.lookup-seconds", 30);
            plugin.getLogger().log(Level.INFO, "[DEBUG Place] Vérification CoreProtect (récent BREAK) pour " + blockTypeName + " à " + blockLocation + " dans les " + tempsVerificationSecondes + "s.");

            List<String[]> resultatLookup = cpAPI.blockLookup(blockPlaced.getLocation().getBlock(), tempsVerificationSecondes);

            if (resultatLookup != null && !resultatLookup.isEmpty()) {
                for (String[] donneesResultat : resultatLookup) {
                    CoreProtectAPI.ParseResult resultatParse = cpAPI.parseResult(donneesResultat);
                    plugin.getLogger().log(Level.FINER, "[DEBUG Place] Entrée CoreProtect: Joueur=" + resultatParse.getPlayer() + ", Action=" + resultatParse.getActionString() + ", Type=" + resultatParse.getType().name());

                    if (resultatParse.getActionId() == 0 && // 0 = BlockBreak
                            resultatParse.getType() == blockType) {

                        boolean memeJoueur = player.getName().equalsIgnoreCase(resultatParse.getPlayer());
                        boolean memeEntreprise = false;

                        EntrepriseManagerLogic.Entreprise entrepriseJoueurActuel = entrepriseLogic.getEntrepriseDuJoueur(player);
                        if (entrepriseJoueurActuel != null) {
                            String nomJoueurCasseur = resultatParse.getPlayer();
                            String nomEntrepriseCasseur = entrepriseLogic.getNomEntrepriseDuMembre(nomJoueurCasseur);
                            if (nomEntrepriseCasseur != null && entrepriseJoueurActuel.getNom().equals(nomEntrepriseCasseur)) {
                                memeEntreprise = true;
                            }
                        }

                        if (memeJoueur || memeEntreprise) {
                            preventRevenueForPlace = true;
                            player.sendMessage(ChatColor.YELLOW + "[Entreprise] Anti-Duplication: Ce bloc a été récemment cassé et replacé. Aucun revenu pour ce placement.");
                            plugin.getLogger().log(Level.INFO, "[DEBUG Place] Anti-Duplication (récent BREAK): " + player.getName() + " plaçant " + blockTypeName + ". Revenu bloqué.");
                            break; // Sortir de la boucle, une correspondance suffit
                        }
                    }
                }
            } else {
                plugin.getLogger().log(Level.INFO, "[DEBUG Place] CoreProtect n'a retourné aucune action récente de type BREAK pour ce bloc/emplacement.");
            }
        } else {
            plugin.getLogger().warning("[DEBUG Place] API CoreProtect non disponible. Vérification anti-duplication (récent BREAK) sautée.");
        }

        // Vérifier les restrictions de placement générales (votre logique existante)
        boolean isBlockedByRestriction = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_PLACE", blockTypeName, 1);
        if (isBlockedByRestriction) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.INFO, "[DEBUG Place] Action bloquée par restriction standard pour " + player.getName() + " plaçant " + blockTypeName + ". Événement annulé.");
            return;
        }

        // Si le revenu n'est PAS bloqué par la logique anti-duplication CoreProtect ci-dessus
        if (!preventRevenueForPlace) {
            plugin.getLogger().log(Level.INFO, "[DEBUG Place] Enregistrement action productive pour " + player.getName() + " plaçant " + blockTypeName);
            entrepriseLogic.enregistrerActionProductive(player, "BLOCK_PLACE", blockType, 1, blockPlaced);
        } else {
            // Si preventRevenueForPlace est true, on logue simplement que l'action productive pour le revenu est sautée.
            // Le bloc est toujours placé (sauf si annulé par une autre restriction), mais sans gain.
            plugin.getLogger().log(Level.INFO, "[DEBUG Place] Action productive pour revenu SAUTÉE pour " + player.getName() + " plaçant " + blockTypeName + " (cause: anti-duplication CoreProtect).");
        }
        plugin.getLogger().log(Level.INFO, "[DEBUG Place] Fin: " + player.getName() + " plaçant " + blockTypeName);
    }
}