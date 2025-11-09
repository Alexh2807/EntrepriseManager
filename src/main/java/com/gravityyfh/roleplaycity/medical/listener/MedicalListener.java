package com.gravityyfh.roleplaycity.medical.listener;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.medical.data.InjuredPlayer;
import com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.spigotmc.event.entity.EntityDismountEvent;

public class MedicalListener implements Listener {
    private final RoleplayCity plugin;
    private final MedicalSystemManager medicalManager;

    public MedicalListener(RoleplayCity plugin) {
        this.plugin = plugin;
        this.medicalManager = plugin.getMedicalSystemManager();
    }

    /**
     * Gère les dégâts par entité (combat)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Si le joueur est déjà blessé, annuler
        if (medicalManager.isInjured(player)) {
            event.setCancelled(true);
            return;
        }

        // Vérifier si le joueur va mourir
        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth <= 0.0 && !hasTotemOfUndying(player)) {
            event.setCancelled(true);
            handlePlayerDowned(player, "Combat");
        }
    }

    /**
     * Gère les dégâts par environnement
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // Si le joueur est déjà blessé, annuler
        if (medicalManager.isInjured(player)) {
            event.setCancelled(true);
            return;
        }

        // Vérifier les causes qui font tomber au sol
        EntityDamageEvent.DamageCause cause = event.getCause();
        boolean shouldDown = cause == EntityDamageEvent.DamageCause.FALL ||
                cause == EntityDamageEvent.DamageCause.LAVA ||
                cause == EntityDamageEvent.DamageCause.FIRE ||
                cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                cause == EntityDamageEvent.DamageCause.DROWNING ||
                cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                cause == EntityDamageEvent.DamageCause.SUICIDE;

        if (!shouldDown) {
            return;
        }

        // Vérifier si le joueur va mourir
        double finalHealth = player.getHealth() - event.getFinalDamage();
        if (finalHealth <= 0.0 && !hasTotemOfUndying(player)) {
            event.setCancelled(true);
            String causeText = getCauseText(cause);
            handlePlayerDowned(player, causeText);
        }
    }

    /**
     * Convertit la cause en texte lisible
     */
    private String getCauseText(EntityDamageEvent.DamageCause cause) {
        return switch (cause) {
            case FALL -> "Chute";
            case LAVA -> "Lave";
            case FIRE, FIRE_TICK -> "Feu";
            case DROWNING -> "Noyade";
            case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "Explosion";
            case SUICIDE -> "Suicide";
            default -> "Inconnu";
        };
    }

    /**
     * Vérifie si le joueur a un totem
     */
    private boolean hasTotemOfUndying(Player player) {
        return player.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING ||
                player.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
    }

    /**
     * Appelle le système médical quand un joueur tombe
     */
    private void handlePlayerDowned(Player player, String cause) {
        medicalManager.onPlayerDowned(player, cause);
    }

    /**
     * Empêche le joueur blessé de mourir normalement
     */
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (medicalManager.isInjured(player)) {
            // Le système médical gère déjà la mort
            event.setDeathMessage(null);
        }
    }

    /**
     * Empêche le joueur blessé de descendre de l'armor stand
     */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (medicalManager.isInjured(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Gère la reconnexion d'un joueur (après redémarrage ou déconnexion)
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Le manager gère automatiquement la restauration ou la mort
        medicalManager.onPlayerJoin(player);
    }

    /**
     * Gère le kick du joueur (souvent causé par l'arrêt serveur)
     * RELÈVE le joueur au lieu de le tuer
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        Player player = event.getPlayer();

        // Si le joueur est blessé lors du kick, le relever au lieu de le tuer
        if (medicalManager.isInjured(player)) {
            medicalManager.revivePlayerOnKick(player);
        }
    }

    /**
     * Nettoie si le joueur se déconnecte volontairement
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Déléguer au manager qui gère le nettoyage ET la mort
        medicalManager.handlePlayerDisconnect(player);
    }

    /**
     * Empêche le joueur blessé de casser des blocs
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (medicalManager.isInjured(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêche le joueur blessé d'interagir avec l'inventaire
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (medicalManager.isInjured(player)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Empêche le joueur blessé de changer d'item dans la hotbar
     */
    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (medicalManager.isInjured(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêche le joueur blessé d'interagir
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (medicalManager.isInjured(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêche le joueur blessé de dropper des items
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (medicalManager.isInjured(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    /**
     * Empêche le joueur blessé de se déplacer
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (medicalManager.isInjured(event.getPlayer())) {
            // Permettre seulement la rotation de la tête, pas le mouvement
            if (event.getFrom().getX() != event.getTo().getX() ||
                    event.getFrom().getY() != event.getTo().getY() ||
                    event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Empêche le joueur blessé de régénérer sa vie
     */
    @EventHandler
    public void onEntityRegainHealth(EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (medicalManager.isInjured(player)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Empêche le joueur blessé de téléporter
     */
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (medicalManager.isInjured(event.getPlayer())) {
            // Autoriser seulement les téléportations du plugin (pour la réanimation)
            if (event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Détecte le Shift + Clic droit d'un médecin sur un patient
     */
    @EventHandler
    public void onMedicInteractWithPatient(PlayerInteractAtEntityEvent event) {
        Player medic = event.getPlayer();

        // Vérifier si c'est un joueur qui est cliqué
        if (!(event.getRightClicked() instanceof Player patient)) {
            return;
        }

        // Vérifier si le patient est blessé
        if (!medicalManager.isInjured(patient)) {
            return;
        }

        // Vérifier si le médecin maintient Shift
        if (!medic.isSneaking()) {
            return;
        }

        // Démarrer le processus de soin
        medicalManager.startHealingProcess(medic, patient);
        event.setCancelled(true);
    }
}
