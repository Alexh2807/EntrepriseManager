/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.configuration.file.FileConfiguration
 */
package de.lightplugins.economy.utils;

import de.lightplugins.economy.master.Main;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

public class DebugPrinting {
    public void sendInfo(String message) {
        FileConfiguration fileConfiguration = Main.settings.getConfig();
        boolean debugMode = fileConfiguration.getBoolean("settings.debug");
        if (debugMode) {
            Bukkit.getLogger().log(Level.INFO, "[lightEconomy] " + message);
        }
    }

    public void sendWarning(String message) {
        FileConfiguration fileConfiguration = Main.settings.getConfig();
        boolean debugMode = fileConfiguration.getBoolean("settings.debug");
        if (debugMode) {
            Bukkit.getLogger().log(Level.WARNING, "[lightEconomy] " + message);
        }
    }

    public void sendError(String message) {
        FileConfiguration fileConfiguration = Main.settings.getConfig();
        boolean debugMode = fileConfiguration.getBoolean("settings.debug");
        if (debugMode) {
            Bukkit.getLogger().log(Level.SEVERE, "[lightEconomy] " + message);
        }
    }
}

