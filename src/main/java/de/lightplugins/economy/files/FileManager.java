/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.configuration.Configuration
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.configuration.file.YamlConfiguration
 */
package de.lightplugins.economy.files;

import de.lightplugins.economy.master.Main;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class FileManager {
    private final Main plugin;
    private FileConfiguration dataConfig = null;
    private File configFile = null;
    private final String configName;

    public FileManager(Main plugin, String configName) {
        this.plugin = plugin;
        this.configName = configName;
        this.saveDefaultConfig(configName);
    }

    public void reloadConfig(String configName) {
        if (this.configFile == null) {
            this.configFile = new File(this.plugin.getLightEconomyDataFolder(), configName);
        }
        this.plugin.reloadConfig();
        this.dataConfig = YamlConfiguration.loadConfiguration((File)this.configFile);
        InputStream defaultStream = this.plugin.getResource(configName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(defaultStream));
            this.dataConfig.setDefaults((Configuration)defaultConfig);
        }
    }

    public FileConfiguration getConfig() {
        if (this.dataConfig == null) {
            this.reloadConfig(this.configName);
        }
        return this.dataConfig;
    }

    public void saveConfig() {
        if (this.dataConfig == null || this.configFile == null) {
            return;
        }
        try {
            this.getConfig().save(this.configFile);
        } catch (IOException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save config to " + this.configFile, e);
        }
    }

    private void saveDefaultConfig(String configName) {
        if (this.configFile == null) {
            this.configFile = new File(this.plugin.getLightEconomyDataFolder(), this.configName);
        }
        if (!this.configFile.exists()) {
            this.plugin.saveResource(configName, false);
        } else {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration((Reader)new InputStreamReader(Objects.requireNonNull(this.plugin.getResource(configName))));
            FileConfiguration existingConfig = this.getConfig();
            for (String key : defaultConfig.getKeys(true)) {
                if (existingConfig.getKeys(true).contains(key)) continue;
                Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Found \u00a7cnon existing config key\u00a7r. Adding \u00a7c" + key + " \u00a7rinto \u00a7c" + configName);
                existingConfig.set(key, defaultConfig.get(key));
            }
            try {
                existingConfig.save(this.configFile);
                Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Your config \u00a7c" + configName + " \u00a7ris up to date.");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.saveConfig();
        }
    }
}

