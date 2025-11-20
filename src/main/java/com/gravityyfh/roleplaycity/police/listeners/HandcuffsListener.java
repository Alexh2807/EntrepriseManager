package com.gravityyfh.roleplaycity.police.listeners;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.HandcuffedPlayerData;
import com.gravityyfh.roleplaycity.police.items.PoliceItemManager;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Listener pour gérer l'utilisation des menottes
 * Gère l'application des menottes et les restrictions des joueurs menottés
 */
public class HandcuffsListener implements Listener {

    private final RoleplayCity plugin;
    private final PoliceItemManager itemManager;
    private final HandcuffedPlayerData handcuffedData;

    // Cooldown pour éviter le double-clic (UUID du handcuffer -> timestamp)
    private final java.util.Map<java.util.UUID, Long> handcuffCooldown = new java.util.HashMap<>();

    public HandcuffsListener(RoleplayCity plugin, PoliceItemManager itemManager, HandcuffedPlayerData handcuffedData) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.handcuffedData = handcuffedData;
    }

    /**
     * Gère l'utilisation des menottes (clic droit sur un joueur)
     * Priorité HIGH pour s'exécuter avant HandcuffsFollowListener
     */
    @EventHandler(priority = EventPriority.HIGH)
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

        ItemStack item = handcuffer.getInventory().getItemInMainHand();

        // Vérifier si on utilise des menottes
        if (!itemManager.isHandcuffs(item)) {
            return;
        }

        event.setCancelled(true);

        // Vérifier le cooldown pour éviter le double-clic (500ms)
        java.util.UUID handcufferUUID = handcuffer.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (handcuffCooldown.containsKey(handcufferUUID)) {
            long lastUse = handcuffCooldown.get(handcufferUUID);
            if (currentTime - lastUse < 500) { // 500ms de cooldown
                return; // Ignorer le double-clic
            }
        }

        // Mettre à jour le cooldown
        handcuffCooldown.put(handcufferUUID, currentTime);

        // Nettoyer les cooldowns expirés (toutes les 10 utilisations)
        if (handcuffCooldown.size() > 10) {
            handcuffCooldown.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > 5000
            );
        }

        // Si le joueur est déjà menotté, le libérer
        if (handcuffedData.isHandcuffed(target)) {
            removeHandcuffs(handcuffer, target);
            return;
        }

        // Vérifier si la cible peut être menottée
        if (!canBeHandcuffed(handcuffer, target)) {
            return;
        }

        // Menotter la cible
        handcuffTarget(handcuffer, target);
    }

    /**
     * Vérifie si une cible peut être menottée
     */
    private boolean canBeHandcuffed(Player handcuffer, Player target) {
        // Vérifier l'invulnérabilité
        if (target.isInvulnerable()) {
            handcuffer.sendMessage("§cCette cible ne peut pas être menottée!");
            return false;
        }

        // Vérifier la permission bypass
        if (target.hasPermission("roleplaycity.police.bypass.handcuffs")) {
            handcuffer.sendMessage("§cCette cible ne peut pas être menottée!");
            return false;
        }

        // Vérifier si on peut se menotter soi-même
        if (handcuffer.equals(target)) {
            boolean canSelfHandcuff = plugin.getConfig().getBoolean("police-equipment.handcuffs.can-self-handcuff", false);
            if (!canSelfHandcuff) {
                handcuffer.sendMessage("§cVous ne pouvez pas vous menotter vous-même!");
                return false;
            }
        }

        return true;
    }

    /**
     * Applique les menottes sur une cible
     */
    private void handcuffTarget(Player handcuffer, Player target) {
        FileConfiguration config = plugin.getConfig();

        // Titre de la boss bar
        String bossBarTitle = config.getString("police-equipment.handcuffs.boss-bar-title",
            "§fSanté des Menottes - Shift pour affaiblir");

        // Menotter
        handcuffedData.handcuff(target, bossBarTitle);

        // Messages
        String handcufferMsg = config.getString("police-equipment.handcuffs.messages.handcuffed",
            "§aVous avez menotté §6%target%§a!");
        handcufferMsg = handcufferMsg.replace("%target%", target.getName());
        handcuffer.sendMessage(handcufferMsg);

        String targetMsg = config.getString("police-equipment.handcuffs.messages.you-are-handcuffed",
            "§cVous avez été menotté par §6%player%§c!");
        targetMsg = targetMsg.replace("%player%", handcuffer.getName());
        target.sendMessage(targetMsg);

        // Son
        String soundName = config.getString("police-equipment.handcuffs.sound", "BLOCK_IRON_DOOR_CLOSE");
        float volume = (float) config.getDouble("police-equipment.handcuffs.sound-volume", 1.0);
        float pitch = (float) config.getDouble("police-equipment.handcuffs.sound-pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName);
            target.playSound(target.getLocation(), sound, volume, pitch);
            handcuffer.playSound(handcuffer.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Son invalide pour menottes: " + soundName);
        }

        // Retirer une menotte de l'inventaire si configuré
        boolean consumeOnUse = config.getBoolean("police-equipment.handcuffs.consume-on-use", false);
        if (consumeOnUse) {
            ItemStack item = handcuffer.getInventory().getItemInMainHand();
            if (item.getAmount() > 1) {
                item.setAmount(item.getAmount() - 1);
            } else {
                handcuffer.getInventory().setItemInMainHand(null);
            }
        }
    }

    /**
     * Retire les menottes d'un joueur
     */
    private void removeHandcuffs(Player handcuffer, Player target) {
        handcuffedData.removeHandcuffs(target);

        FileConfiguration config = plugin.getConfig();

        // Messages
        String handcufferMsg = config.getString("police-equipment.handcuffs.messages.removed-handcuffs",
            "§aVous avez libéré §6%target%§a!");
        handcufferMsg = handcufferMsg.replace("%target%", target.getName());
        handcuffer.sendMessage(handcufferMsg);

        String targetMsg = config.getString("police-equipment.handcuffs.messages.you-are-freed",
            "§aVous avez été libéré par §6%player%§a!");
        targetMsg = targetMsg.replace("%player%", handcuffer.getName());
        target.sendMessage(targetMsg);

        // Son
        String soundName = config.getString("police-equipment.handcuffs.sound-remove", "BLOCK_IRON_DOOR_OPEN");
        float volume = (float) config.getDouble("police-equipment.handcuffs.sound-volume", 1.0);
        float pitch = (float) config.getDouble("police-equipment.handcuffs.sound-pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName);
            target.playSound(target.getLocation(), sound, volume, pitch);
            handcuffer.playSound(handcuffer.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Son invalide pour menottes: " + soundName);
        }
    }

    // ===== RESTRICTIONS POUR LES JOUEURS MENOTTÉS =====

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!handcuffedData.isHandcuffed(player)) {
            return;
        }

        // Bloquer tout mouvement horizontal (sauf si en mode follow)
        if (handcuffedData.getFollowingMap().containsValue(player.getUniqueId())) {
            // Le joueur est en train d'être suivi, ne pas bloquer le mouvement
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();

        if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (handcuffedData.isHandcuffed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (handcuffedData.isHandcuffed(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!handcuffedData.isHandcuffed(player)) {
            return;
        }

        // Liste des commandes autorisées
        List<String> allowedCommands = plugin.getConfig()
            .getStringList("police-equipment.handcuffs.allowed-commands");

        String command = event.getMessage().toLowerCase().split(" ")[0].substring(1);

        boolean allowed = false;
        for (String allowedCmd : allowedCommands) {
            if (command.equalsIgnoreCase(allowedCmd)) {
                allowed = true;
                break;
            }
        }

        if (!allowed) {
            event.setCancelled(true);
            player.sendMessage("§cVous ne pouvez pas utiliser de commandes pendant que vous êtes menotté!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerBreakBlock(BlockBreakEvent event) {
        if (handcuffedData.isHandcuffed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerPlaceBlock(BlockPlaceEvent event) {
        if (handcuffedData.isHandcuffed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (handcuffedData.isHandcuffed(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerDropItem(PlayerDropItemEvent event) {
        if (handcuffedData.isHandcuffed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerSwapHands(PlayerSwapHandItemsEvent event) {
        if (handcuffedData.isHandcuffed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHandcuffedPlayerInteract(PlayerInteractEvent event) {
        if (handcuffedData.isHandcuffed(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Réappliquer les menottes si nécessaire (persistance)
        if (handcuffedData.isHandcuffed(player)) {
            handcuffedData.reapplyHandcuffs(player);
        }
    }

    /**
     * Nettoie les effets visuels lors de la déconnexion
     * MAIS garde le joueur dans la liste (persistance)
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (handcuffedData.isHandcuffed(player)) {
            // Si configuré pour tuer à la déco
            boolean killOnQuit = plugin.getConfig().getBoolean("police-equipment.handcuffs.kill-if-quit", false);
            if (killOnQuit) {
                player.setHealth(0);
                handcuffedData.removeHandcuffs(player); // Si mort, on retire
            } else {
                // Sinon on garde menotté, mais on nettoie juste la BossBar temporairement
                // (HandcuffedPlayerData.removeHandcuffs retire TOUT, donc on ne l'utilise pas ici)
                // On veut juste nettoyer la BossBar pour éviter les fuites mémoire
                // Mais HandcuffedPlayerData ne permet pas de juste cacher la barre sans retirer le joueur...
                // ATTENTION: HandcuffedPlayerData.removeHandcuffs retire de la map !
                // Il faut modifier ou contourner.
                // Pour l'instant, la BossBar se nettoie automatiquement quand le joueur quitte (Bukkit handle ça)
                // Mais HandcuffedPlayerData garde une référence.
                // La solution propre : Ne rien faire ici (la BossBar disparaitra visuellement).
                // HandcuffedPlayerData gardera la référence BossBar invalide, mais reapplyHandcuffs écrasera au join.
                
                // UPDATE: Pour être propre, on devrait avoir une méthode 'suspendHandcuffs' dans Data.
                // Mais pour ce fix rapide : ne rien faire suffit, sauf si killOnQuit.
            }
        }
    }
}
