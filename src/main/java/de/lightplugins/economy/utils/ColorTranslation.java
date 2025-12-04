/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.md_5.bungee.api.ChatColor
 *  org.bukkit.Bukkit
 */
package de.lightplugins.economy.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;

public class ColorTranslation {
    private final Pattern pattern = Pattern.compile("#[a-fA-F0-9]{6}");

    public String hexTranslation(String msg) {
        if (Bukkit.getVersion().contains("1.16") || Bukkit.getVersion().contains("1.17") || Bukkit.getVersion().contains("1.18") || Bukkit.getVersion().contains("1.19") || Bukkit.getVersion().contains("1.20")) {
            if (msg.contains("&#")) {
                msg = msg.replace("&#", "#");
            }
            Matcher match = this.pattern.matcher(msg);
            while (match.find()) {
                String color = msg.substring(match.start(), match.end());
                msg = msg.replace(color, String.valueOf(ChatColor.of((String)color)));
                match = this.pattern.matcher(msg);
            }
        }
        return ChatColor.translateAlternateColorCodes((char)'&', (String)msg);
    }
}

