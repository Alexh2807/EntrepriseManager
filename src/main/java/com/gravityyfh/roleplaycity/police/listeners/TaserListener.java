package com.gravityyfh.roleplaycity.police.listeners;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.TasedPlayerData;
import com.gravityyfh.roleplaycity.police.items.PoliceItemManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceManager;
import com.gravityyfh.roleplaycity.service.ProfessionalServiceType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

/**
 * Listener pour gérer l'utilisation du taser
 * Gère le clic droit, les effets sur la cible, et les restrictions des joueurs tasés
 */
public class TaserListener implements Listener {

    private final RoleplayCity plugin;
    private final PoliceItemManager itemManager;
    private final TasedPlayerData tasedData;

    public TaserListener(RoleplayCity plugin, PoliceItemManager itemManager, TasedPlayerData tasedData) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.tasedData = tasedData;
    }

    /**
     * Gère l'utilisation du taser (clic droit)
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Vérifier si le joueur utilise un taser
        if (item == null || !itemManager.isTaser(item)) {
            return;
        }

        // Vérifier si c'est un clic droit
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        event.setCancelled(true);

        // Vérifier si le joueur est lui-même tasé
        if (tasedData.isTased(player)) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§cVous ne pouvez pas utiliser le taser pendant que vous êtes tasé!"));
            return;
        }

        // Vérifier le cooldown
        if (tasedData.isOnCooldown(player)) {
            int remaining = tasedData.getCooldownRemaining(player);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§cCooldown: §6" + remaining + "s"));
            return;
        }

        // Vérifier les charges
        int charges = itemManager.getTaserCharges(item);
        if (charges <= 0) {
            player.sendMessage("§cVotre taser n'a plus de charges!");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        // Chercher une cible dans un rayon de 3 blocs
        Player target = getNearestPlayer(player, 3.0);

        if (target == null) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§cAucune cible à portée (3 blocs)"));
            return;
        }

        // Vérifier si la cible peut être tasée
        if (!canBeTased(player, target)) {
            return;
        }

        // Taser la cible
        taserTarget(player, target);

        // Utiliser une charge
        itemManager.useTaserCharge(item);

        // Ajouter le cooldown
        FileConfiguration config = plugin.getConfig();
        int cooldown = config.getInt("police-equipment.taser.cooldown", 10);
        tasedData.addCooldown(player, cooldown);

        // Son et effets visuels pour le tireur
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        player.sendMessage("§aVous avez tasé §6" + target.getName() + "§a!");
    }

    /**
     * Trouve le joueur le plus proche dans un rayon donné
     */
    private Player getNearestPlayer(Player source, double radius) {
        Player nearest = null;
        double nearestDistance = radius;

        Location sourceLoc = source.getEyeLocation();
        org.bukkit.util.Vector direction = sourceLoc.getDirection();

        for (Entity entity : source.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Player target)) {
                continue;
            }

            // Vérifier que le joueur regarde dans la direction de la cible
            org.bukkit.util.Vector toTarget = target.getLocation().toVector()
                .subtract(source.getLocation().toVector())
                .normalize();

            double dot = direction.dot(toTarget);
            if (dot < 0.7) { // ~45 degrés de cône
                continue;
            }

            double distance = source.getLocation().distance(target.getLocation());
            if (distance < nearestDistance) {
                nearest = target;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    /**
     * Vérifie si une cible peut être tasée
     */
    private boolean canBeTased(Player taser, Player target) {
        // Vérifier si le policier est en service
        ProfessionalServiceManager serviceManager = plugin.getProfessionalServiceManager();
        if (serviceManager != null && !serviceManager.isInService(taser.getUniqueId(), ProfessionalServiceType.POLICE)) {
            serviceManager.sendNotInServiceMessage(taser, ProfessionalServiceType.POLICE);
            return false;
        }

        // Vérifier si déjà tasé
        if (tasedData.isTased(target)) {
            taser.sendMessage("§c" + target.getName() + " est déjà tasé!");
            return false;
        }

        // Vérifier l'invulnérabilité
        if (target.isInvulnerable()) {
            taser.sendMessage("§cCette cible ne peut pas être tasée!");
            return false;
        }

        // Vérifier la permission bypass
        if (target.hasPermission("roleplaycity.police.bypass.taser")) {
            taser.sendMessage("§cCette cible ne peut pas être tasée!");
            return false;
        }

        // Vérifier le bouclier
        if (target.isBlocking()) {
            taser.sendMessage("§c" + target.getName() + " a bloqué le taser avec son bouclier!");
            target.sendMessage("§aVous avez bloqué un taser avec votre bouclier!");
            return false;
        }

        return true;
    }

    /**
     * Applique les effets du taser sur une cible
     */
    private void taserTarget(Player taser, Player target) {
        FileConfiguration config = plugin.getConfig();
        int duration = config.getInt("police-equipment.taser.duration", 5);

        // Marquer comme tasé
        tasedData.addTased(target, duration);

        // Titre et sous-titre (avec traduction des couleurs)
        String title = ChatColor.translateAlternateColorCodes('&',
            config.getString("police-equipment.taser.title", "&c&lVOUS ÊTES TASÉ!"));
        String subtitle = ChatColor.translateAlternateColorCodes('&',
            config.getString("police-equipment.taser.subtitle", "&7Immobilisé pendant &6%seconds%s"));
        subtitle = subtitle.replace("%seconds%", String.valueOf(duration));

        target.sendTitle(title, subtitle, 10, duration * 20, 20);

        // Effets de potion
        if (config.getBoolean("police-equipment.taser.effects.blindness.enabled", true)) {
            int blindDuration = config.getInt("police-equipment.taser.effects.blindness.duration", 5);
            int blindAmplifier = config.getInt("police-equipment.taser.effects.blindness.amplifier", 1);
            target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                blindDuration * 20, blindAmplifier, false, false));
        }

        if (config.getBoolean("police-equipment.taser.effects.slowness.enabled", true)) {
            int slowDuration = config.getInt("police-equipment.taser.effects.slowness.duration", 5);
            int slowAmplifier = config.getInt("police-equipment.taser.effects.slowness.amplifier", 1);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOW,
                slowDuration * 20, slowAmplifier, false, false));
        }

        // Son
        String soundName = config.getString("police-equipment.taser.sound", "ENTITY_PLAYER_HURT");
        float volume = (float) config.getDouble("police-equipment.taser.sound-volume", 1.0);
        float pitch = (float) config.getDouble("police-equipment.taser.sound-pitch", 1.5);

        try {
            Sound sound = Sound.valueOf(soundName);
            target.playSound(target.getLocation(), sound, volume, pitch);

            // Son pour les joueurs à proximité
            target.getWorld().playSound(target.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Son invalide pour taser: " + soundName);
        }

        // Particules
        if (config.getBoolean("police-equipment.taser.particles.enabled", true)) {
            String particleName = config.getString("police-equipment.taser.particles.type", "VILLAGER_ANGRY");

            try {
                Particle particle = Particle.valueOf(particleName);
                Location loc = target.getLocation();

                // 4 particules autour du joueur
                for (int i = 0; i < 4; i++) {
                    double angle = (Math.PI / 2) * i;
                    double x = loc.getX() + Math.cos(angle);
                    double z = loc.getZ() + Math.sin(angle);
                    target.getWorld().spawnParticle(particle, x, loc.getY() + 1, z, 5);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Particule invalide pour taser: " + particleName);
            }
        }

        // Tâche de suppression automatique après la durée
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (tasedData.isTased(target)) {
                    tasedData.removeTased(target);
                    target.sendMessage("§aVous n'êtes plus tasé.");
                }
            }
        }.runTaskLater(plugin, duration * 20L);

        tasedData.setTasedTask(target.getUniqueId(), task);

        // Message au joueur
        target.sendMessage("§cVous avez été tasé par §6" + taser.getName() + "§c!");
    }

    // ===== RESTRICTIONS POUR LES JOUEURS TASÉS =====

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTasedPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!tasedData.isTased(player)) {
            return;
        }

        // Bloquer tout mouvement horizontal
        Location from = event.getFrom();
        Location to = event.getTo();

        if (to != null && (from.getX() != to.getX() || from.getZ() != to.getZ())) {
            event.setTo(from);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTasedPlayerDropItem(PlayerDropItemEvent event) {
        if (tasedData.isTased(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTasedPlayerInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            if (tasedData.isTased(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTasedPlayerSwapHands(PlayerSwapHandItemsEvent event) {
        if (tasedData.isTased(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTasedPlayerInteract(PlayerInteractEvent event) {
        if (tasedData.isTased(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTasedPlayerDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            if (tasedData.isTased(player)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTasedPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();

        if (!tasedData.isTased(player)) {
            return;
        }

        // Liste des commandes autorisées pendant qu'on est tasé
        List<String> allowedCommands = plugin.getConfig().getStringList("police-equipment.taser.allowed-commands");

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
            player.sendMessage("§cVous ne pouvez pas utiliser de commandes pendant que vous êtes tasé!");
        }
    }

    /**
     * Nettoie les données lors de la déconnexion
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Si le joueur était tasé
        if (tasedData.isTased(player)) {
            boolean killOnQuit = plugin.getConfig().getBoolean("police-equipment.taser.kill-if-quit", false);

            if (killOnQuit) {
                player.setHealth(0);
            }

            tasedData.removeTased(player);
        }

        // Nettoyer le cooldown
        tasedData.removeCooldown(player);
    }
}
