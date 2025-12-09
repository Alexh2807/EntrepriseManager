/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package de.lightplugins.economy.utils;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.lightplugins.economy.master.Main;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PluginMessaging {
    public void sendMessageThrowBungeeNetwork(String channelType, Player player, String message) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(channelType);
        out.writeUTF(player.getName());
        out.writeUTF(Main.colorTranslation.hexTranslation(message));
        player.sendPluginMessage(Main.getInstance.asPlugin(), "BungeeCord", out.toByteArray());
    }
}

