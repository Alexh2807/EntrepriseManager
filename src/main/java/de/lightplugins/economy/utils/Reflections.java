/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.utils;

import de.lightplugins.economy.master.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class Reflections {
    private Main plugin;

    public Reflections(Main plugin) {
        this.plugin = plugin;
    }

    public void sendPacket(Player player, Object packet) {
        try {
            Object handle = player.getClass().getMethod("getHandle", new Class[0]).invoke(player, new Object[0]);
            Object playerConnection = handle.getClass().getField("playerConnection").get(handle);
            playerConnection.getClass().getMethod("sendPacket", this.getNMSClass("Packet")).invoke(playerConnection, packet);
        } catch (Exception ex) {
            throw new RuntimeException("ERROR", ex);
        }
    }

    public Class<?> getNMSClass(String name) {
        try {
            return Class.forName("net.minecraft.server." + Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3] + "." + name);
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("ERROR", ex);
        }
    }
}

