/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.configuration.file.FileConfiguration
 */
package de.lightplugins.economy.utils;

import de.lightplugins.economy.database.querys.BankTableAsync;
import de.lightplugins.economy.master.Main;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;

public class BankLevelSystem {
    public Main plugin;

    public BankLevelSystem(Main plugin) {
        this.plugin = plugin;
    }

    private int getCurrentBankLevel(UUID owner) {
        int currentLevel;
        BankTableAsync bankTable = new BankTableAsync(this.plugin);
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer((UUID)owner);
        CompletableFuture<Integer> completableFuture = bankTable.playerCurrentBankLevel(offlinePlayer.getName());
        try {
            currentLevel = completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return currentLevel;
    }

    private int getCurrentBankLevelString(String owner) {
        int currentLevel;
        BankTableAsync bankTable = new BankTableAsync(this.plugin);
        CompletableFuture<Integer> completableFuture = bankTable.playerCurrentBankLevel(owner);
        try {
            if (completableFuture.get() == null) {
                return 0;
            }
            currentLevel = completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        return currentLevel;
    }

    public double getLimitByLevel(UUID owner) {
        int level = this.getCurrentBankLevel(owner);
        FileConfiguration levels = Main.bankLevelMenu.getConfig();
        for (String value : Objects.requireNonNull(levels.getConfigurationSection("levels")).getKeys(false)) {
            if (level != levels.getInt("levels." + value + ".level")) continue;
            return levels.getDouble("levels." + value + ".max-value");
        }
        return 0.1;
    }

    public double getLimitByLevelString(String owner) {
        int level = this.getCurrentBankLevelString(owner);
        FileConfiguration levels = Main.bankLevelMenu.getConfig();
        for (String value : Objects.requireNonNull(levels.getConfigurationSection("levels")).getKeys(false)) {
            if (level != levels.getInt("levels." + value + ".level")) continue;
            return levels.getDouble("levels." + value + ".max-value");
        }
        return 0.1;
    }
}

