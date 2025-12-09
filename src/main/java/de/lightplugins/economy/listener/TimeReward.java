/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.EconomyResponse
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 */
package de.lightplugins.economy.listener;

import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.master.Main;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TimeReward {
    public void startTimedReward() {
        FileConfiguration settings = Main.settings.getConfig();
        String currencyNamePlural = Main.economyImplementer.currencyNameSingular();
        String currencyNameSingular = Main.economyImplementer.currencyNameSingular();
        final boolean enabledMoney = settings.getBoolean("settings.timeReward.money.enable");
        final double moneyAmount = settings.getDouble("settings.timeReward.money.amount");
        final int intervalSeconds = settings.getInt("settings.timeReward.money.intervall");
        final int intervallInMinutes = intervalSeconds / 60;
        new BukkitRunnable(){

            public void run() {
                if (!enabledMoney) {
                    return;
                }
                long currentMillisInSeconds = System.currentTimeMillis() / 1000L;
                if (currentMillisInSeconds % (long)intervalSeconds == 0L) {
                    Bukkit.getServer().getOnlinePlayers().forEach(singlePlayer -> {
                        if (singlePlayer == null) {
                            return;
                        }
                        EconomyResponse economyResponse = Main.economyImplementer.depositPlayer((OfflinePlayer)singlePlayer, moneyAmount);
                        if (economyResponse.transactionSuccess()) {
                            Main.util.sendMessage((Player)singlePlayer, MessagePath.TimeRewardMoney.getPath().replace("#amount#", String.valueOf(moneyAmount)).replace("#currency#", Main.util.getCurrency(moneyAmount)).replace("#intervallMinutes#", String.valueOf(intervallInMinutes)).replace("#intervallSeconds#", String.valueOf(intervalSeconds)));
                        }
                    });
                }
            }
        }.runTaskTimerAsynchronously(Main.getInstance.asPlugin(), 0L, 20L);
    }
}

