package com.gravityyfh.entreprisemanager;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.ChatColor; // Ajouté
import org.bukkit.plugin.Plugin; // Ajouté
import org.bukkit.Bukkit; // Ajouté

import net.coreprotect.CoreProtect; // Ajouté
import net.coreprotect.CoreProtectAPI; // Ajouté

import java.util.List; // Ajouté
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) // Intervenir tôt pour la vérification
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();
        Material blockType = block.getType();
        String blockTypeName = blockType.name();

        if (player.getGameMode() == GameMode.CREATIVE) {
            plugin.getLogger().log(Level.FINER, "[DEBUG Break] Ignoré (Créatif): " + player.getName() + " cassant " + blockTypeName);
            return;
        }

        plugin.getLogger().log(Level.INFO, "[DEBUG Break] Début: " + player.getName() + " cassant " + blockTypeName + " à " + blockLocation.toString());

        // Scénario CoreProtect : Le joueur casse un bloc.
        // On vérifie si ce bloc a été précédemment posé par UN JOUEUR (n'importe lequel).
        // Si oui, pas de revenu.
        CoreProtectAPI cpAPI = getCoreProtectAPI();
        boolean blockWasPlayerPlacedByCoreProtect = false;

        if (cpAPI != null) {
            // Temps de recherche pour l'historique (ex: 30 jours). Configurable.
            int tempsLookupHistoriqueSecondes = plugin.getConfig().getInt("anti-duplication.block-break.history-lookup-seconds", 86400 * 30);
            plugin.getLogger().log(Level.INFO, "[DEBUG Break] Vérification CoreProtect (bloc posé par joueur?) pour " + blockTypeName + " à " + blockLocation + " sur les " + tempsLookupHistoriqueSecondes + " dernières secondes.");

            List<String[]> resultatLookup = cpAPI.blockLookup(block, tempsLookupHistoriqueSecondes);

            if (resultatLookup != null && !resultatLookup.isEmpty()) {
                for (String[] donneesResultat : resultatLookup) {
                    CoreProtectAPI.ParseResult resultatParse = cpAPI.parseResult(donneesResultat);
                    plugin.getLogger().log(Level.FINER, "[DEBUG Break] Entrée CoreProtect: Joueur=" + resultatParse.getPlayer() + ", Action=" + resultatParse.getActionString() + ", Type=" + resultatParse.getType().name() + " à X:" + resultatParse.getX() + " Y:" + resultatParse.getY() + " Z:" + resultatParse.getZ());

                    // On cherche la dernière action significative qui a mis ce bloc ici.
                    // Si c'était un placement par un joueur, alors on considère le bloc comme "posé par un joueur".
                    if (resultatParse.getActionId() == 1 && // 1 = BlockPlace
                            resultatParse.getType() == blockType && // Même type de bloc
                            resultatParse.getX() == blockLocation.getBlockX() &&
                            resultatParse.getY() == blockLocation.getBlockY() &&
                            resultatParse.getZ() == blockLocation.getBlockZ()) {

                        blockWasPlayerPlacedByCoreProtect = true;
                        plugin.getLogger().log(Level.INFO, "[DEBUG Break] Bloc (" + blockTypeName + ") détecté comme ayant été posé par '" + resultatParse.getPlayer() + "' via CoreProtect. Revenu bloqué pour " + player.getName());
                        player.sendMessage(ChatColor.YELLOW + "[Entreprise] Ce bloc a été précédemment posé par un joueur. Aucun revenu généré.");
                        break; // Une fois qu'on sait qu'il a été posé, on peut arrêter la recherche.
                    }
                }
                if (!blockWasPlayerPlacedByCoreProtect) {
                    plugin.getLogger().log(Level.INFO, "[DEBUG Break] CoreProtect n'a pas trouvé d'action de placement pour ce bloc. Considéré comme naturel/non-joueur.");
                }
            } else {
                plugin.getLogger().log(Level.INFO, "[DEBUG Break] CoreProtect n'a retourné aucune action pour ce bloc/emplacement. Considéré comme naturel/non-joueur.");
            }
        } else {
            plugin.getLogger().warning("[DEBUG Break] API CoreProtect non disponible. Vérification (bloc posé par joueur) sautée.");
        }

        // --- Suppression de l'ancien système ---
        // boolean etaitPoseParJoueur = entrepriseLogic.estBlocPoseParJoueur(blockLocation);
        // if (etaitPoseParJoueur) {
        // entrepriseLogic.demarquerBlocCommePoseParJoueur(blockLocation);
        // plugin.getLogger().log(Level.FINER, "[DEBUG Break] Bloc (" + blockTypeName + ") à " + blockLocation.toString() + " démarqué (était posé par joueur).");
        // }
        // --- Fin Suppression ---

        // Vérifier les restrictions de cassage (votre logique existante)
        boolean isBlockedByRestriction = entrepriseLogic.verifierEtGererRestrictionAction(player, "BLOCK_BREAK", blockTypeName, 1);
        plugin.getLogger().log(Level.INFO, "[DEBUG Break] Résultat vérification restriction standard pour " + player.getName() + " cassant " + blockTypeName + " : " + (isBlockedByRestriction ? "BLOQUÉ" : "AUTORISÉ"));

        if (isBlockedByRestriction) {
            event.setCancelled(true);
            plugin.getLogger().log(Level.INFO, "[DEBUG Break] Action bloquée par restriction standard pour " + player.getName() + " cassant " + blockTypeName + ". Événement annulé.");
            return;
        }

        // Si le bloc a été posé par un joueur (détecté par CoreProtect),
        // OU si les restrictions standards ont bloqué l'action, ne pas donner de revenu.
        // La vérification isBlockedByRestriction a déjà fait un return si true.
        if (blockWasPlayerPlacedByCoreProtect) {
            // L'action productive n'est pas appelée pour le revenu car le bloc était posé par un joueur.
            // Le message a déjà été envoyé au joueur par la logique CoreProtect.
            plugin.getLogger().log(Level.INFO, "[DEBUG Break] Action productive pour revenu SAUTÉE pour " + player.getName() + " cassant " + blockTypeName + " (cause: bloc posé par joueur détecté par CoreProtect).");
        } else {
            // Le bloc est considéré comme naturel (ou non posé par un joueur selon CoreProtect) ET non bloqué par restrictions
            plugin.getLogger().log(Level.INFO, "[DEBUG Break] Enregistrement action productive pour " + player.getName() + " cassant " + blockTypeName);
            entrepriseLogic.enregistrerActionProductive(player, "BLOCK_BREAK", blockType, 1, block);
        }
        plugin.getLogger().log(Level.INFO, "[DEBUG Break] Fin: " + player.getName() + " cassant " + blockTypeName);
    }
}