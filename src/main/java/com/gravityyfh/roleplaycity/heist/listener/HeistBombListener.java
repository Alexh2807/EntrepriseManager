package com.gravityyfh.roleplaycity.heist.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.heist.data.Heist;
import com.gravityyfh.roleplaycity.heist.data.HeistPhase;
import com.gravityyfh.roleplaycity.heist.data.PlacedBomb;
import com.gravityyfh.roleplaycity.heist.gui.BombDefuseGUI;
import com.gravityyfh.roleplaycity.heist.manager.HeistManager;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.data.TownRole;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import dev.lone.itemsadder.api.CustomFurniture;
import dev.lone.itemsadder.api.Events.FurnitureBreakEvent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.UUID;

/**
 * Listener pour les interactions avec la bombe de cambriolage.
 * Gère:
 * - Confirmation de la bombe posée (clic droit sur l'item au sol)
 * - Désamorçage par la police (clic droit sur une bombe active)
 * - Récupération de la bombe (casser si non armée)
 */
public class HeistBombListener implements Listener {

    private final RoleplayCity plugin;
    private final HeistManager heistManager;
    private final TownManager townManager;
    private final ClaimManager claimManager;

    public HeistBombListener(RoleplayCity plugin, HeistManager heistManager) {
        this.plugin = plugin;
        this.heistManager = heistManager;
        this.townManager = plugin.getTownManager();
        this.claimManager = plugin.getClaimManager();
    }

    /**
     * Gère le clic droit sur une entité (Item au sol ou ArmorStand)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        // === CAS 1: Item au sol (bombe physique) ===
        if (entity instanceof Item item) {
            // Vérifier si c'est une bombe de heist (via le tag)
            if (!item.getScoreboardTags().contains("heist_bomb")) {
                return;
            }

            event.setCancelled(true);

            // Chercher si c'est une bombe posée en attente de confirmation
            PlacedBomb placedBomb = heistManager.getPlacedBombByArmorStand(item.getUniqueId());
            if (placedBomb != null) {
                handlePlacedBombInteraction(player, placedBomb, item);
                return;
            }

            // Sinon, chercher si c'est une bombe de heist actif
            Heist heist = findHeistByBombEntity(item.getUniqueId());
            if (heist != null) {
                handleActiveHeistBombInteraction(player, heist);
            }
            return;
        }

        // === CAS 2: ArmorStand (ancien système / furniture) ===
        if (entity instanceof ArmorStand) {
            plugin.getLogger().info("[HeistDebug] Clic sur ArmorStand: " + entity.getUniqueId());

            // PRIORITÉ 1: Vérifier si c'est un furniture de bombe ItemsAdder
            boolean isBombFurn = isBombFurniture(entity);
            plugin.getLogger().info("[HeistDebug] isBombFurniture = " + isBombFurn);

            if (isBombFurn) {
                event.setCancelled(true);

                // Vérifier si c'est une bombe trackée (vient d'être posée)
                PlacedBomb placedBomb = heistManager.getPlacedBombByArmorStand(entity.getUniqueId());
                if (placedBomb != null) {
                    plugin.getLogger().info("[HeistDebug] Bombe trackée trouvée");
                    handlePlacedBombInteraction(player, placedBomb, entity);
                    return;
                }

                // Vérifier si c'est une bombe de heist actif
                Heist heist = findHeistByBombEntity(entity.getUniqueId());
                if (heist != null) {
                    plugin.getLogger().info("[HeistDebug] Heist actif trouvé");
                    handleActiveHeistBombInteraction(player, heist);
                    return;
                }

                // Sinon, c'est une bombe orpheline (après restart) → Réactiver
                plugin.getLogger().info("[HeistDebug] Bombe orpheline détectée → Réactivation");
                handleUnknownBombActivation(player, entity);
                return;
            }

            // Si ce n'est pas un furniture de bombe, vérifier quand même les maps (ancien système)
            PlacedBomb placedBomb = heistManager.getPlacedBombByArmorStand(entity.getUniqueId());
            if (placedBomb != null) {
                event.setCancelled(true);
                handlePlacedBombInteraction(player, placedBomb, entity);
                return;
            }

            Heist heist = findHeistByBombEntity(entity.getUniqueId());
            if (heist != null) {
                event.setCancelled(true);
                handleActiveHeistBombInteraction(player, heist);
            }
        }
    }

    /**
     * Gère l'interaction avec une bombe posée.
     * - Si NON armée : N'importe qui peut l'activer avec clic droit
     * - Si DÉJÀ armée : Police/Maire/Adjoint peuvent la désamorcer, autres reçoivent un message
     */
    private void handlePlacedBombInteraction(Player player, PlacedBomb bomb, Entity bombEntity) {
        // === CAS 1: Bombe DÉJÀ ARMÉE (timer en cours) ===
        if (bomb.isTimerStarted()) {
            // Chercher le heist actif - d'abord par plotKey, sinon par location
            Heist activeHeist = null;

            if (bomb.getPlotKey() != null) {
                activeHeist = heistManager.getActiveHeist(bomb.getPlotKey());
            }

            // Fallback: chercher par location si pas trouvé par plotKey
            if (activeHeist == null) {
                activeHeist = heistManager.getActiveHeistAt(bomb.getLocation());
            }

            if (activeHeist != null) {
                // Heist actif trouvé - déléguer à la gestion du heist (désamorçage pour police)
                plugin.getLogger().info("[HeistDebug] Heist actif trouvé, redirection vers handleActiveHeistBombInteraction");
                handleActiveHeistBombInteraction(player, activeHeist);
            } else {
                // Bombe armée mais pas de heist (explosion simple en cours)
                plugin.getLogger().info("[HeistDebug] Bombe armée sans heist actif - explosion simple");
                // Vérifier si c'est un policier qui pourrait désamorcer
                if (bomb.isOnClaimedPlot()) {
                    Town town = townManager.getTown(bomb.getTownName());
                    if (town != null) {
                        TownRole role = town.getMemberRole(player.getUniqueId());
                        boolean isPolice = role == TownRole.POLICIER;

                        if (isPolice) {
                            player.sendMessage(ChatColor.YELLOW + "Cette bombe est en cours d'explosion simple (hors heist).");
                            player.sendMessage(ChatColor.GRAY + "Impossible de la désamorcer.");
                        } else {
                            player.sendMessage(ChatColor.RED + "⚠ Cette bombe est déjà armée !");
                            player.sendMessage(ChatColor.YELLOW + "Le compte à rebours est en cours...");
                        }
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "⚠ Cette bombe est déjà armée !");
                    player.sendMessage(ChatColor.YELLOW + "Le compte à rebours est en cours...");
                }
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
            return;
        }

        // === CAS 2: Bombe NON ARMÉE - Activer ===

        // Vérifier si déjà en attente de confirmation
        if (bomb.isAwaitingConfirmation()) {
            player.sendMessage(ChatColor.YELLOW + "Confirmation en cours...");
            return;
        }

        // Marquer comme en attente de confirmation
        bomb.setAwaitingConfirmation(true);

        // Effet sonore d'amorçage
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 0.5f);

        // Confirmer la bombe (déclenche le heist ou l'explosion simple)
        Heist heist = heistManager.confirmBomb(player, bomb, bombEntity.getUniqueId());

        if (heist != null) {
            // Cambriolage démarré sur terrain claimé
            plugin.getLogger().info("[Heist] Bombe confirmée par " + player.getName() +
                " - Cambriolage démarré sur " + bomb.getTownName() + "/" + bomb.getPlotId());
        } else if (!bomb.isOnClaimedPlot()) {
            // Explosion simple hors zone
            plugin.getLogger().info("[Heist] Bombe confirmée par " + player.getName() +
                " - Explosion simple (hors zone claimée)");
        }
    }

    /**
     * Gère l'interaction avec une bombe de heist actif (pour désamorçage police)
     */
    private void handleActiveHeistBombInteraction(Player player, Heist heist) {
        // Vérifier que le heist est en phase countdown
        if (heist.getPhase() != HeistPhase.COUNTDOWN) {
            player.sendMessage(ChatColor.RED + "La bombe a déjà explosé!");
            return;
        }

        // Vérifier si le joueur est policier dans cette ville
        Town town = townManager.getTown(heist.getTownName());
        if (town == null) {
            player.sendMessage(ChatColor.RED + "Erreur: Ville introuvable.");
            return;
        }

        TownRole role = town.getMemberRole(player.getUniqueId());
        boolean isPolice = role == TownRole.POLICIER;

        if (!isPolice) {
            // Vérifier la permission de bypass
            if (!player.hasPermission("roleplaycity.heist.defuse")) {
                // Si c'est un criminel, proposer de rejoindre
                if (!heist.isParticipant(player.getUniqueId())) {
                    tryJoinHeist(player, heist);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Vous participez déjà à ce cambriolage. Attendez l'explosion!");
                }
                return;
            }
        }

        // Ouvrir le GUI de désamorçage pour la police
        BombDefuseGUI.openFor(plugin, heistManager, heist, player);
    }

    /**
     * Trouve le heist correspondant à une entité de bombe
     */
    private Heist findHeistByBombEntity(UUID entityId) {
        for (Heist heist : heistManager.getActiveHeists()) {
            if (heist.getBombEntityId() != null && heist.getBombEntityId().equals(entityId)) {
                return heist;
            }
        }
        return null;
    }

    /**
     * Permet à un joueur de rejoindre un heist en cours (phase countdown ou robbery)
     * en cliquant sur l'entité de la bombe ou en étant proche
     */
    public void tryJoinHeist(Player player, Heist heist) {
        // Vérifier que le heist est actif
        if (!heist.isActive()) {
            player.sendMessage(ChatColor.RED + "Ce cambriolage est terminé.");
            return;
        }

        // Vérifier si le joueur n'est pas déjà participant
        if (heist.isParticipant(player.getUniqueId())) {
            player.sendMessage(ChatColor.YELLOW + "Vous participez déjà à ce cambriolage.");
            return;
        }

        // Vérifier si le joueur n'est pas policier
        Town town = townManager.getTown(heist.getTownName());
        if (town != null) {
            TownRole role = town.getMemberRole(player.getUniqueId());
            if (role == TownRole.POLICIER) {
                player.sendMessage(ChatColor.RED + "Vous êtes policier, vous ne pouvez pas participer au cambriolage!");
                return;
            }
        }

        // Ajouter comme participant
        heist.addParticipant(player.getUniqueId(), player.getName(), false);
        player.sendMessage(ChatColor.translateAlternateColorCodes('&',
            heistManager.getConfig().getMessage("heist-join")));

        plugin.getLogger().info("[Heist] " + player.getName() + " a rejoint le cambriolage sur "
            + heist.getPlotKey());
    }

    // =========================================================================
    // PROTECTION DE LA BOMBE
    // =========================================================================

    @EventHandler
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!isHeistEntity(entity)) return;

        // Vérifier si c'est une bombe armée ou non
        if (entity instanceof ArmorStand) {
            PlacedBomb bomb = heistManager.getPlacedBombByArmorStand(entity.getUniqueId());
            if (bomb != null && !bomb.isTimerStarted()) {
                // Bombe non armée - ne pas bloquer les dégâts (permet la destruction via ItemsAdder)
                return;
            }
        }

        // Bombe armée ou hologramme - bloquer
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        Entity entity = event.getEntity();
        if (!isHeistEntity(entity)) return;

        // Vérifier si c'est une bombe armée ou non
        if (entity instanceof ArmorStand) {
            PlacedBomb bomb = heistManager.getPlacedBombByArmorStand(entity.getUniqueId());
            if (bomb != null && !bomb.isTimerStarted()) {
                // Bombe non armée - ne pas bloquer (permet la destruction)
                return;
            }

            // Si c'est un joueur qui tape sur une bombe armée, lui dire pourquoi
            if (event.getDamager() instanceof Player player) {
                if (bomb != null && bomb.isTimerStarted()) {
                    player.sendMessage(ChatColor.RED + "⚠ Cette bombe est armée !");
                }
            }
        }

        // Bombe armée ou hologramme - bloquer
        event.setCancelled(true);
    }

    @EventHandler
    public void onArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        if (isHeistEntity(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    // DESTRUCTION DE LA BOMBE (ItemsAdder FurnitureBreakEvent)
    // =========================================================================

    /**
     * Gère la tentative de destruction d'une bombe via ItemsAdder.
     * - Si la bombe n'est PAS armée (timer non démarré) : autoriser la casse et retirer du système
     * - Si la bombe EST armée : bloquer avec message d'erreur
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onFurnitureBreak(FurnitureBreakEvent event) {
        CustomFurniture furniture = event.getFurniture();
        if (furniture == null) return;

        // Vérifier si c'est une bombe
        String namespacedId = furniture.getNamespacedID();
        if (namespacedId == null || (!namespacedId.contains("bomb") && !namespacedId.contains("bombe"))) {
            return; // Pas une bombe
        }

        Player player = event.getPlayer();
        if (player == null) return;

        // Récupérer l'ArmorStand de la bombe
        UUID armorStandId;
        try {
            armorStandId = furniture.getArmorstand().getUniqueId();
        } catch (Exception e) {
            return;
        }

        // Vérifier si cette bombe est trackée dans notre système
        PlacedBomb bomb = heistManager.getPlacedBombByArmorStand(armorStandId);

        if (bomb != null) {
            // Bombe trackée - vérifier si elle est armée
            if (bomb.isTimerStarted()) {
                // BOMBE ARMÉE - Bloquer la destruction
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "⚠ Cette bombe est armée et ne peut pas être récupérée !");
                player.sendMessage(ChatColor.YELLOW + "Le compte à rebours est en cours...");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // BOMBE NON ARMÉE - Autoriser la destruction et retirer du système
            heistManager.removePlacedBomb(armorStandId);
            player.sendMessage(ChatColor.GREEN + "✓ Bombe récupérée avec succès.");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
            plugin.getLogger().info("[Heist] Bombe récupérée par " + player.getName());
            // L'événement n'est PAS annulé, ItemsAdder va dropper l'item
            return;
        }

        // Vérifier si c'est une bombe d'un heist actif
        Heist heist = heistManager.getHeistByBombEntity(armorStandId);
        if (heist != null) {
            // BOMBE DE HEIST ACTIF - Toujours bloquer
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "⚠ Cette bombe est active dans un cambriolage !");
            player.sendMessage(ChatColor.YELLOW + "Elle ne peut pas être récupérée.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Bombe non trackée (orpheline) - autoriser la destruction
        // C'est probablement une bombe qui a été placée avant l'installation du plugin
        // ou une bombe corrompue
        player.sendMessage(ChatColor.YELLOW + "Bombe récupérée.");
    }

    /**
     * Détecte si une entité est un furniture "bombe_cambriolage" d'ItemsAdder
     */
    private boolean isBombFurniture(Entity entity) {
        if (!(entity instanceof ArmorStand)) {
            plugin.getLogger().info("[HeistDebug] Pas un ArmorStand");
            return false;
        }

        try {
            // Vérifier si c'est un CustomFurniture ItemsAdder
            Class<?> customFurnitureClass = Class.forName("dev.lone.itemsadder.api.CustomFurniture");
            java.lang.reflect.Method byAlreadySpawnedMethod = customFurnitureClass.getMethod("byAlreadySpawned", Entity.class);
            Object customFurniture = byAlreadySpawnedMethod.invoke(null, entity);

            plugin.getLogger().info("[HeistDebug] CustomFurniture = " + customFurniture);

            if (customFurniture != null) {
                java.lang.reflect.Method getNamespacedIDMethod = customFurnitureClass.getMethod("getNamespacedID");
                String namespacedID = (String) getNamespacedIDMethod.invoke(customFurniture);

                plugin.getLogger().info("[HeistDebug] NamespacedID = " + namespacedID);

                // Vérifier si c'est bien une bombe de cambriolage
                // Peut être "my_items:bomb" ou autre variante contenant "bomb"
                boolean isBomb = namespacedID != null &&
                    (namespacedID.contains("bomb") || namespacedID.contains("bombe"));
                plugin.getLogger().info("[HeistDebug] isBomb = " + isBomb);
                return isBomb;
            }
        } catch (Exception e) {
            // ItemsAdder non disponible ou erreur
            plugin.getLogger().warning("[HeistDebug] Erreur isBombFurniture: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Gère l'activation d'une bombe non trackée (après restart ou récupérée)
     * Cette bombe est physiquement présente mais pas dans placedBombs
     *
     * IMPORTANT: Vérifie d'abord s'il y a déjà un heist actif à cet endroit
     *
     * FIX: Si le joueur est policier/maire/adjoint et qu'un heist est actif,
     * on ouvre directement le GUI de désamorçage au lieu de proposer de réarmer
     */
    private void handleUnknownBombActivation(Player player, Entity bombEntity) {
        Location bombLoc = bombEntity.getLocation();

        // === VÉRIFICATION 1: Y a-t-il déjà un heist actif à cet emplacement? ===
        Heist existingHeist = heistManager.getActiveHeistAt(bombLoc);
        if (existingHeist != null) {
            plugin.getLogger().info("[HeistDebug] Heist actif trouvé à cet emplacement - redirection vers désamorçage");

            // FIX Bug 1: Mettre à jour l'UUID de la bombe dans le heist si nécessaire
            if (existingHeist.getBombEntityId() != null &&
                !existingHeist.getBombEntityId().equals(bombEntity.getUniqueId())) {
                plugin.getLogger().info("[HeistDebug] Mise à jour UUID bombe heist: " +
                    existingHeist.getBombEntityId() + " -> " + bombEntity.getUniqueId());
                existingHeist.setBombEntityId(bombEntity.getUniqueId());

                // Mettre aussi à jour dans placedBombs si présent
                PlacedBomb existingBomb = findArmedBombAtLocation(bombLoc);
                if (existingBomb != null) {
                    heistManager.updateBombArmorStandId(existingBomb, bombEntity.getUniqueId());
                }
            }

            handleActiveHeistBombInteraction(player, existingHeist);
            return;
        }

        // === VÉRIFICATION 2: Y a-t-il une bombe armée dans placedBombs pour ce terrain? ===
        // (L'UUID peut avoir changé mais la bombe est toujours là)
        PlacedBomb existingBomb = findArmedBombAtLocation(bombLoc);
        if (existingBomb != null && existingBomb.isTimerStarted()) {
            plugin.getLogger().info("[HeistDebug] Bombe déjà armée trouvée à cet emplacement");

            // FIX Bug 1: Mettre à jour l'UUID de l'ArmorStand dans le système de manière synchronisée
            heistManager.updateBombArmorStandIdSafe(existingBomb, bombEntity.getUniqueId());

            // Traiter comme une bombe déjà armée
            handleArmedBombInteraction(player, existingBomb, bombEntity);
            return;
        }

        // === VÉRIFICATION 3: Le joueur est-il policier? ===
        // Si oui, il ne devrait pas pouvoir armer la bombe - on lui indique qu'elle n'est pas active
        boolean isPolice = isPlayerPoliceAtLocation(player, bombLoc);
        if (isPolice) {
            player.sendMessage(ChatColor.YELLOW + "Cette bombe n'est pas armée.");
            player.sendMessage(ChatColor.GRAY + "Elle doit être activée par un cambrioleur avant de pouvoir être désamorcée.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // === Pas de heist ni de bombe armée - C'est une nouvelle activation ===

        // NETTOYER les hologrammes orphelins autour de cette bombe (après restart)
        cleanupOrphanHolograms(bombLoc);

        // Créer un PlacedBomb temporaire pour cette bombe
        PlacedBomb bomb = new PlacedBomb(
            player.getUniqueId(),
            player.getName(),
            bombLoc,
            bombEntity.getUniqueId()
        );

        // Vérifier si c'est sur un terrain claimé
        heistManager.checkIfOnClaimedPlot(bomb);

        // Enregistrer la bombe AVANT de l'armer
        heistManager.registerPlacedBombDirect(bomb);

        // Effet sonore d'amorçage
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 0.5f);

        // Confirmer la bombe immédiatement
        Heist heist = heistManager.confirmBomb(player, bomb, bombEntity.getUniqueId());

        if (heist != null) {
            plugin.getLogger().info("[Heist] Bombe réactivée après restart par " + player.getName() +
                " - Cambriolage démarré sur " + bomb.getTownName() + "/" + bomb.getPlotId());
        } else if (!bomb.isOnClaimedPlot()) {
            plugin.getLogger().info("[Heist] Bombe réactivée après restart par " + player.getName() +
                " - Explosion simple (hors zone claimée)");
        }
    }

    /**
     * Vérifie si un joueur est policier/maire/adjoint dans la ville où se trouve la location
     */
    private boolean isPlayerPoliceAtLocation(Player player, Location location) {
        String townName = claimManager.getClaimOwner(location);
        if (townName == null) return false;

        Town town = townManager.getTown(townName);
        if (town == null) return false;

        TownRole role = town.getMemberRole(player.getUniqueId());
        return role == TownRole.POLICIER;
    }

    /**
     * Cherche une bombe armée dans placedBombs proche de la location donnée
     */
    private PlacedBomb findArmedBombAtLocation(Location location) {
        for (PlacedBomb bomb : heistManager.getAllPlacedBombs()) {
            if (bomb.isTimerStarted()) {
                Location bombLoc = bomb.getLocation();
                if (bombLoc.getWorld().equals(location.getWorld())) {
                    // Vérifier si c'est dans le même chunk ou très proche (5 blocs)
                    if (bombLoc.distance(location) < 5) {
                        return bomb;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Gère l'interaction avec une bombe déjà armée (timer en cours)
     * - Police/Maire/Adjoint : GUI de désamorçage (pour heist) ou désamorçage rapide (explosion simple)
     * - Autres : Message d'erreur
     *
     * FIX Bug 3: La police peut maintenant désamorcer les explosions simples
     */
    private void handleArmedBombInteraction(Player player, PlacedBomb bomb, Entity bombEntity) {
        // Chercher le heist actif - d'abord par plotKey, sinon par location, sinon par proximité
        Heist activeHeist = findHeistForBomb(bomb);

        if (activeHeist != null) {
            // Heist actif trouvé - déléguer à la gestion du heist (désamorçage pour police)
            plugin.getLogger().info("[HeistDebug] handleArmedBombInteraction: Heist trouvé, redirection");
            handleActiveHeistBombInteraction(player, activeHeist);
            return;
        }

        // Bombe armée sans heist (explosion simple en cours)
        plugin.getLogger().info("[HeistDebug] handleArmedBombInteraction: Pas de heist, explosion simple");

        // Vérifier le rôle du joueur
        boolean isPolice = isPlayerPoliceAtLocation(player, bomb.getLocation());

        // FIX Bug 3: Permettre à la police de désamorcer les explosions simples
        if (isPolice || player.hasPermission("roleplaycity.heist.defuse")) {
            // Proposer le désamorçage rapide (sans GUI)
            player.sendMessage(ChatColor.YELLOW + "Cette bombe est armée (explosion simple - pas de cambriolage).");
            player.sendMessage(ChatColor.GREEN + "➤ Cliquez à nouveau pour désamorcer cette bombe.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);

            // Marquer la bombe comme "en attente de désamorçage"
            // On utilise awaitingConfirmation pour le double-clic
            if (bomb.isAwaitingConfirmation()) {
                // Deuxième clic = désamorçage
                defuseSimpleBomb(player, bomb, bombEntity);
            } else {
                bomb.setAwaitingConfirmation(true);
                // Reset après 5 secondes si pas de confirmation
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (bomb.isAwaitingConfirmation() && bomb.isTimerStarted()) {
                        bomb.setAwaitingConfirmation(false);
                    }
                }, 100L); // 5 secondes
            }
        } else {
            player.sendMessage(ChatColor.RED + "⚠ Cette bombe est déjà armée !");
            player.sendMessage(ChatColor.YELLOW + "Le compte à rebours est en cours...");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * Désamorce une bombe simple (hors cambriolage)
     * FIX Bug 3: Nouvelle méthode pour permettre le désamorçage des explosions simples
     */
    private void defuseSimpleBomb(Player player, PlacedBomb bomb, Entity bombEntity) {
        plugin.getLogger().info("[Heist] Police " + player.getName() + " désamorce une bombe simple");

        // Supprimer la bombe du système
        heistManager.removePlacedBomb(bomb.getArmorStandId());

        // Supprimer l'entité physique
        try {
            CustomFurniture furniture = CustomFurniture.byAlreadySpawned(bombEntity);
            if (furniture != null) {
                furniture.remove(true); // true = dropper l'item
            } else {
                bombEntity.remove();
            }
        } catch (Throwable e) {
            bombEntity.remove();
        }

        // Nettoyer les hologrammes orphelins autour
        cleanupOrphanHolograms(bomb.getLocation());
        cleanupSimpleTimerHolograms(bomb.getLocation());

        // Messages et effets
        player.sendMessage(ChatColor.GREEN + "✓ Bombe désamorcée avec succès !");
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);

        // Broadcast
        plugin.getServer().broadcastMessage(ChatColor.GREEN + "🛡 La police a désamorcé une bombe !");
    }

    /**
     * Cherche un heist actif correspondant à une bombe
     * FIX Bug 5: Amélioration de la recherche par plotKey ET location ET proximité
     */
    private Heist findHeistForBomb(PlacedBomb bomb) {
        // 1. Par plotKey exact
        if (bomb.getPlotKey() != null) {
            Heist heist = heistManager.getActiveHeist(bomb.getPlotKey());
            if (heist != null) return heist;
        }

        // 2. Par location (gère les terrains groupés)
        Heist heist = heistManager.getActiveHeistAt(bomb.getLocation());
        if (heist != null) return heist;

        // 3. Par proximité (pour les cas de UUID changés)
        for (Heist activeHeist : heistManager.getActiveHeists()) {
            Location heistLoc = activeHeist.getBombLocation();
            Location bombLoc = bomb.getLocation();

            if (heistLoc.getWorld().equals(bombLoc.getWorld()) &&
                heistLoc.distance(bombLoc) < 5) {
                return activeHeist;
            }
        }

        return null;
    }

    /**
     * Nettoie les hologrammes de timer simple orphelins
     * FIX Bug 4: Ajout du nettoyage des hologrammes heist_simple_timer
     */
    private void cleanupSimpleTimerHolograms(Location location) {
        int removed = 0;
        for (Entity entity : location.getWorld().getNearbyEntities(location, 5, 5, 5)) {
            if (entity instanceof ArmorStand) {
                if (entity.getScoreboardTags().contains("heist_simple_timer")) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            plugin.getLogger().info("[Heist] " + removed + " timer(s) simple(s) orphelin(s) nettoyé(s)");
        }
    }

    /**
     * Nettoie les hologrammes de heist orphelins près d'une bombe
     * (hologrammes qui persistent après un restart)
     *
     * FIX Bug 4: Nettoyage complet de tous les types d'hologrammes heist
     */
    private void cleanupOrphanHolograms(Location bombLocation) {
        int removed = 0;
        // Chercher dans un rayon de 5 blocs autour de la bombe
        for (Entity entity : bombLocation.getWorld().getNearbyEntities(bombLocation, 5, 5, 5)) {
            if (entity instanceof ArmorStand) {
                // FIX Bug 4: Vérifier tous les tags possibles d'hologrammes heist
                if (entity.getScoreboardTags().contains("heist_hologram") ||
                    entity.getScoreboardTags().contains("heist_simple_timer") ||
                    entity.getScoreboardTags().contains("heist_countdown_timer") ||
                    entity.getScoreboardTags().contains("heist_robbery_timer")) {

                    // Vérifier que cet hologramme n'appartient pas à un heist actif
                    boolean belongsToActiveHeist = false;
                    UUID entityId = entity.getUniqueId();
                    for (Heist heist : heistManager.getActiveHeists()) {
                        if (heist.getHologramEntityIds().contains(entityId)) {
                            belongsToActiveHeist = true;
                            break;
                        }
                    }

                    if (!belongsToActiveHeist) {
                        entity.remove();
                        removed++;
                    }
                }
            }
        }

        if (removed > 0) {
            plugin.getLogger().info("[Heist] " + removed + " hologramme(s) orphelin(s) nettoyé(s) près de la bombe");
        }
    }

    /**
     * Vérifie si l'entité est une bombe ou un hologramme de heist actif
     */
    private boolean isHeistEntity(Entity entity) {
        if (entity == null) return false;

        // Vérifier tag scoreboard simple (si utilisé)
        if (entity.getScoreboardTags().contains("heist_bomb")) return true;

        UUID uuid = entity.getUniqueId();

        // Vérifier dans les bombes posées (non confirmées)
        if (heistManager.getPlacedBombByArmorStand(uuid) != null) return true;

        // Vérifier dans les heists actifs (bombe physique ou hologramme)
        for (Heist heist : heistManager.getActiveHeists()) {
            if (uuid.equals(heist.getBombEntityId())) return true;
            if (heist.getHologramEntityIds().contains(uuid)) return true;
        }

        return false;
    }
}
