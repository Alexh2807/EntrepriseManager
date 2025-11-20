package com.gravityyfh.roleplaycity.police.listeners;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.police.data.HandcuffedPlayerData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Listener pour gérer le système de casse des menottes
 * Les joueurs menottés peuvent se libérer en sneakant à répétition
 */
public class HandcuffsBreakListener implements Listener {

    private final RoleplayCity plugin;
    private final HandcuffedPlayerData handcuffedData;

    public HandcuffsBreakListener(RoleplayCity plugin, HandcuffedPlayerData handcuffedData) {
        this.plugin = plugin;
        this.handcuffedData = handcuffedData;
    }

    /**
     * Gère le sneak pour affaiblir les menottes
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        // Vérifier si le joueur est menotté
        if (!handcuffedData.isPlayerHandcuffed(player)) {
            return;
        }

        // Vérifier si le système de casse est activé
        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("police-equipment.handcuffs.breaking.enabled", true)) {
            return;
        }

        // Vérifier si le joueur commence à sneaker
        if (!event.isSneaking()) {
            return;
        }

        // Obtenir le montant de dégâts par sneak
        // Par défaut : 1.0 / 200 = 0.005 (0.5% par sneak)
        int sneakCount = config.getInt("police-equipment.handcuffs.breaking.sneak-count", 200);
        double decreaseAmount = 1.0 / sneakCount;

        // Endommager les menottes
        boolean broken = handcuffedData.damageHandcuffs(player, decreaseAmount);

        // Obtenir la santé restante
        double health = handcuffedData.getHandcuffsHealth(player.getUniqueId());
        int percentage = (int) (health * 100);

        if (broken) {
            // Menottes cassées !
            String message = config.getString("police-equipment.handcuffs.messages.broken",
                "§a§lVous avez cassé les menottes !");
            player.sendMessage(message);

            // Son de casse
            String breakSoundName = config.getString("police-equipment.handcuffs.sound-break", "ENTITY_ITEM_BREAK");
            float volume = (float) config.getDouble("police-equipment.handcuffs.sound-volume", 1.0);
            float pitch = (float) config.getDouble("police-equipment.handcuffs.sound-pitch", 1.0);

            try {
                Sound sound = Sound.valueOf(breakSoundName);
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Son invalide pour casse de menottes: " + breakSoundName);
            }

        } else {
            // Afficher la progression dans l'action bar
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent("§7Menottes: §6" + percentage + "% §7| Continuez à sneaker..."));

            // Son de craquement
            if (percentage % 10 == 0) { // Tous les 10%
                String crackSoundName = config.getString("police-equipment.handcuffs.sound-crack", "BLOCK_WOOD_BREAK");
                float volume = (float) config.getDouble("police-equipment.handcuffs.sound-volume", 0.5);
                float pitch = (float) config.getDouble("police-equipment.handcuffs.sound-pitch", 1.5);

                try {
                    Sound sound = Sound.valueOf(crackSoundName);
                    player.playSound(player.getLocation(), sound, volume, pitch);
                } catch (IllegalArgumentException e) {
                    // Ignorer l'erreur
                }
            }
        }
    }
}
