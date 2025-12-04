/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Sound
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.utils;

import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class Sounds {
    public void soundOnSuccess(Player player) {
        player.playSound((Entity)player, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        player.playSound((Entity)player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
    }

    public void soundOnFailure(Player player) {
        player.playSound((Entity)player, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.3f);
        player.playSound((Entity)player, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
    }
}

