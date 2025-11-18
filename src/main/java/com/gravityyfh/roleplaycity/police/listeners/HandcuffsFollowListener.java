package com.gravityyfh.roleplaycity.police.listeners;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.HandcuffedPlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Listener pour gérer le système de suivi des joueurs menottés
 * Permet à un joueur de faire suivre un joueur menotté
 */
public class HandcuffsFollowListener implements Listener {

    private final RoleplayCity plugin;
    private final HandcuffedPlayerData handcuffedData;

    // Cooldown pour éviter le double-clic du système de suivi (UUID -> timestamp)
    private final java.util.Map<java.util.UUID, Long> followToggleCooldown = new java.util.HashMap<>();

    // Task pour le mouvement fluide
    private BukkitTask smoothFollowTask;

    public HandcuffsFollowListener(RoleplayCity plugin, HandcuffedPlayerData handcuffedData) {
        this.plugin = plugin;
        this.handcuffedData = handcuffedData;
        startSmoothFollowTask();
    }

    /**
     * Démarre la tâche de suivi fluide (exécutée chaque tick pour ultra fluidité)
     */
    private void startSmoothFollowTask() {
        smoothFollowTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Obtenir la configuration
            FileConfiguration config = plugin.getConfig();
            double pullDistance = config.getDouble("police-equipment.handcuffs.following.pull-distance", 2.5);
            double maxDistance = config.getDouble("police-equipment.handcuffs.following.max-distance", 8.0);
            int stepsBack = config.getInt("police-equipment.handcuffs.following.steps-back", 10);

            // Parcourir tous les suivis actifs
            for (UUID handcufferUUID : handcuffedData.getFollowingMap().keySet()) {
                Player handcuffer = Bukkit.getPlayer(handcufferUUID);
                if (handcuffer == null || !handcuffer.isOnline()) {
                    continue;
                }

                UUID targetUUID = handcuffedData.getFollowedPlayer(handcufferUUID);
                if (targetUUID == null) {
                    continue;
                }

                Player target = Bukkit.getPlayer(targetUUID);
                if (target == null || !target.isOnline()) {
                    continue;
                }

                // Vérifier que les deux sont dans le même monde
                if (!handcuffer.getWorld().equals(target.getWorld())) {
                    continue;
                }

                Location handcufferLoc = handcuffer.getLocation();
                Location targetLoc = target.getLocation();
                double distance = handcufferLoc.distance(targetLoc);

                // Téléportation d'urgence si trop loin
                if (distance > maxDistance) {
                    Location targetLocation = handcuffedData.getPathLocation(handcufferUUID, stepsBack);
                    if (targetLocation != null && targetLocation.getWorld() != null) {
                        org.bukkit.util.Vector direction = handcufferLoc.toVector()
                            .subtract(targetLocation.toVector())
                            .normalize();

                        Location teleportLoc = targetLocation.clone();
                        teleportLoc.setDirection(direction);
                        target.teleport(teleportLoc);
                    }
                }
                // Traction fluide avec vélocité PURE (sans téléportation)
                else if (distance > pullDistance) {
                    // Calculer le vecteur de direction vers le handcuffer
                    org.bukkit.util.Vector direction = handcufferLoc.toVector()
                        .subtract(targetLoc.toVector())
                        .normalize();

                    // Force progressive basée sur la distance (formule améliorée)
                    double force = Math.min((distance - pullDistance) * 0.8, 1.2);

                    // Créer le vecteur de vélocité
                    org.bukkit.util.Vector velocity = direction.multiply(force);

                    // Gestion intelligente de la gravité
                    double currentY = target.getVelocity().getY();
                    double yDiff = handcufferLoc.getY() - targetLoc.getY();

                    // Si le joueur tombe
                    if (currentY < -0.1) {
                        // Atténuer la chute pour un mouvement plus naturel
                        velocity.setY(Math.max(velocity.getY(), -0.3));
                    }
                    // Si le joueur monte (sauter)
                    else if (currentY > 0.1) {
                        // Conserver le momentum de saut
                        velocity.setY(Math.min(velocity.getY() + currentY * 0.6, 0.9));
                    }
                    // Ajustement automatique de la hauteur
                    else {
                        if (yDiff > 1.5) {
                            // Handcuffer plus haut: tirer vers le haut
                            velocity.setY(Math.min(yDiff * 0.4, 0.7));
                        } else if (yDiff < -1.5) {
                            // Handcuffer plus bas: pousser vers le bas doucement
                            velocity.setY(Math.max(yDiff * 0.3, -0.3));
                        } else {
                            // Même hauteur approximative: maintenir stabilité
                            velocity.setY(velocity.getY() * 0.5);
                        }
                    }

                    // Appliquer la vélocité (SANS téléportation)
                    target.setVelocity(velocity);
                }
            }
        }, 1L, 1L); // Exécuté CHAQUE TICK (20 fois par seconde) pour ultra fluidité
    }

    /**
     * Arrête la tâche de suivi fluide
     */
    public void stopSmoothFollowTask() {
        if (smoothFollowTask != null) {
            smoothFollowTask.cancel();
            smoothFollowTask = null;
        }
    }

    /**
     * Gère le toggle du système de suivi (clic droit sur joueur menotté SANS menottes)
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player handcuffer = event.getPlayer();
        Entity entity = event.getRightClicked();

        // IMPORTANT: Vérifier que c'est la main principale (évite double événement main/off-hand)
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }

        // Vérifier si c'est un joueur
        if (!(entity instanceof Player target)) {
            return;
        }

        // IMPORTANT: Si le joueur tient des menottes en main, ne pas gérer le follow
        // C'est HandcuffsListener qui doit gérer ce cas (menotter/démenotter)
        org.bukkit.inventory.ItemStack mainHand = handcuffer.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType() != org.bukkit.Material.AIR) {
            // Vérifier si c'est des menottes via le manager
            if (plugin.getPoliceItemManager() != null &&
                plugin.getPoliceItemManager().isHandcuffs(mainHand)) {
                // Le joueur tient des menottes, laisser HandcuffsListener gérer
                return;
            }
        }

        // Vérifier si le système de suivi est activé
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("police-equipment.handcuffs.following.enabled", true)) {
            return;
        }

        // Vérifier si la cible est menottée
        if (!handcuffedData.isHandcuffed(target)) {
            return;
        }

        // Vérifier le cooldown pour éviter le double-clic (500ms)
        java.util.UUID handcufferUUID = handcuffer.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (followToggleCooldown.containsKey(handcufferUUID)) {
            long lastUse = followToggleCooldown.get(handcufferUUID);
            if (currentTime - lastUse < 500) { // 500ms de cooldown
                event.setCancelled(true);
                return; // Ignorer le double-clic
            }
        }

        // Mettre à jour le cooldown
        followToggleCooldown.put(handcufferUUID, currentTime);

        // Nettoyer les cooldowns expirés (toutes les 10 utilisations)
        if (followToggleCooldown.size() > 10) {
            followToggleCooldown.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 5000
            );
        }

        // Vérifier la permission (optionnel)
        if (config.getBoolean("police-equipment.handcuffs.following.require-permission", false)) {
            if (!handcuffer.hasPermission("roleplaycity.police.follow")) {
                return;
            }
        }

        // Vérifier si le joueur fait déjà suivre quelqu'un
        if (handcuffedData.isFollowing(handcuffer.getUniqueId())) {
            UUID currentFollowed = handcuffedData.getFollowedPlayer(handcuffer.getUniqueId());

            // Si c'est le même joueur, arrêter le suivi
            if (currentFollowed.equals(target.getUniqueId())) {
                stopFollowing(handcuffer, target);
                event.setCancelled(true);
                return;
            } else {
                // Changer de cible
                handcuffedData.stopFollowing(handcuffer.getUniqueId());
            }
        }

        // Vérifier si la cible est déjà suivie par quelqu'un d'autre
        for (UUID followerUUID : handcuffedData.getFollowingMap().keySet()) {
            UUID followedUUID = handcuffedData.getFollowedPlayer(followerUUID);
            if (followedUUID != null && followedUUID.equals(target.getUniqueId()) &&
                !followerUUID.equals(handcuffer.getUniqueId())) {

                String message = config.getString("police-equipment.handcuffs.messages.already-following",
                    "§cCe joueur est déjà suivi par quelqu'un d'autre!");
                handcuffer.sendMessage(message);
                event.setCancelled(true);
                return;
            }
        }

        // Démarrer le suivi
        startFollowing(handcuffer, target);
        event.setCancelled(true);
    }

    /**
     * Démarre le suivi d'un joueur menotté
     */
    private void startFollowing(Player handcuffer, Player target) {
        handcuffedData.startFollowing(handcuffer.getUniqueId(), target.getUniqueId());

        FileConfiguration config = plugin.getConfig();

        String handcufferMsg = config.getString("police-equipment.handcuffs.messages.start-following",
            "§a%target% vous suit maintenant!");
        handcufferMsg = handcufferMsg.replace("%target%", target.getName());
        handcuffer.sendMessage(handcufferMsg);

        String targetMsg = config.getString("police-equipment.handcuffs.messages.you-follow",
            "§cVous suivez maintenant §6%player%§c!");
        targetMsg = targetMsg.replace("%player%", handcuffer.getName());
        target.sendMessage(targetMsg);
    }

    /**
     * Arrête le suivi
     */
    private void stopFollowing(Player handcuffer, Player target) {
        handcuffedData.stopFollowing(handcuffer.getUniqueId());

        FileConfiguration config = plugin.getConfig();

        String handcufferMsg = config.getString("police-equipment.handcuffs.messages.stop-following",
            "§a%target% ne vous suit plus.");
        handcufferMsg = handcufferMsg.replace("%target%", target.getName());
        handcuffer.sendMessage(handcufferMsg);

        String targetMsg = config.getString("police-equipment.handcuffs.messages.you-stop-follow",
            "§aVous ne suivez plus §6%player%§a.");
        targetMsg = targetMsg.replace("%player%", handcuffer.getName());
        target.sendMessage(targetMsg);
    }

    /**
     * Enregistre le chemin du handcuffer dans l'historique (pour téléportation d'urgence)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHandcufferMove(PlayerMoveEvent event) {
        Player handcuffer = event.getPlayer();

        // Vérifier si le joueur fait suivre quelqu'un
        if (!handcuffedData.isFollowing(handcuffer.getUniqueId())) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        // Vérifier si le joueur a vraiment bougé (position uniquement)
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }

        // Ajouter la position à l'historique (utilisé pour téléportation d'urgence)
        handcuffedData.addPathLocation(handcuffer.getUniqueId(), to.clone());
    }

    /**
     * Arrête le suivi si le handcuffer vole
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHandcufferFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (!event.isFlying()) {
            return;
        }

        if (handcuffedData.isFollowing(player.getUniqueId())) {
            UUID targetUUID = handcuffedData.getFollowedPlayer(player.getUniqueId());
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : null;

            handcuffedData.stopFollowing(player.getUniqueId());

            player.sendMessage("§cLe suivi s'arrête car vous volez.");
            if (target != null) {
                target.sendMessage("§aVous ne suivez plus " + player.getName() + ".");
            }
        }
    }

    /**
     * Arrête le suivi si le handcuffer utilise l'elytra
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHandcufferGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!event.isGliding()) {
            return;
        }

        if (handcuffedData.isFollowing(player.getUniqueId())) {
            UUID targetUUID = handcuffedData.getFollowedPlayer(player.getUniqueId());
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : null;

            handcuffedData.stopFollowing(player.getUniqueId());

            player.sendMessage("§cLe suivi s'arrête car vous planez.");
            if (target != null) {
                target.sendMessage("§aVous ne suivez plus " + player.getName() + ".");
            }
        }
    }

    /**
     * Nettoie le suivi lors de la déconnexion
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Si le joueur faisait suivre quelqu'un
        if (handcuffedData.isFollowing(uuid)) {
            UUID targetUUID = handcuffedData.getFollowedPlayer(uuid);
            Player target = targetUUID != null ? Bukkit.getPlayer(targetUUID) : null;

            handcuffedData.stopFollowing(uuid);

            if (target != null) {
                target.sendMessage("§a" + player.getName() + " s'est déconnecté, vous ne le suivez plus.");
            }
        }

        // Si le joueur était suivi par quelqu'un
        for (UUID followerUUID : handcuffedData.getFollowingMap().keySet()) {
            UUID followedUUID = handcuffedData.getFollowedPlayer(followerUUID);
            if (followedUUID != null && followedUUID.equals(uuid)) {
                Player follower = Bukkit.getPlayer(followerUUID);
                handcuffedData.stopFollowing(followerUUID);

                if (follower != null) {
                    follower.sendMessage("§c" + player.getName() + " s'est déconnecté, le suivi s'arrête.");
                }
            }
        }
    }
}
