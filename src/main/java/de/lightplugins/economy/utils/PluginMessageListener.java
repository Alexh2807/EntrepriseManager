/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.messaging.PluginMessageListener
 *  org.jetbrains.annotations.NotNull
 */
package de.lightplugins.economy.utils;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import de.lightplugins.economy.enums.PluginMessagePath;
import de.lightplugins.economy.master.Main;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PluginMessageListener
implements org.bukkit.plugin.messaging.PluginMessageListener {
    public void onPluginMessageReceived(String channel, @NotNull Player player, byte[] message) {
        ByteArrayDataInput input;
        String command;
        if (player == null) {
            PluginMessageListener.$$$reportNull$$$0(0);
        }
        if (!(command = (input = ByteStreams.newDataInput(message)).readUTF()).equalsIgnoreCase(PluginMessagePath.PAY.getType())) {
            return;
        }
        String targetName = input.readUTF();
        String args1 = input.readUTF();
        Bukkit.getLogger().log(Level.WARNING, "target: " + targetName);
        Bukkit.getLogger().log(Level.WARNING, "args: " + args1);
        Bukkit.getLogger().log(Level.WARNING, "channel: " + channel);
        Player target = Bukkit.getPlayer((String)targetName);
        if (target == null) {
            Bukkit.getConsoleSender().sendMessage("Target is null");
            return;
        }
        Main.util.sendMessage(target, args1);
    }

    private static /* synthetic */ void $$$reportNull$$$0(int n) {
        throw new IllegalArgumentException(String.format("Argument for @NotNull parameter '%s' of %s.%s must not be null", "player", "de/lightplugins/economy/utils/PluginMessageListener", "onPluginMessageReceived"));
    }
}

