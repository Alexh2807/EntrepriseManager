/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.main;

import de.lightplugins.economy.database.querys.MoneyTableAsync;
import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class DebugCommand
extends SubCommand {
    @Override
    public String getName() {
        return "debug";
    }

    @Override
    public String getDescription() {
        return "Get an overview of the plugins state";
    }

    @Override
    public String getSyntax() {
        return "/le debug";
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length == 1) {
            // Accepter si le joueur a la permission OU s'il est OP
            if (!player.hasPermission(PermissionPath.Debug.getPerm()) && !player.isOp()) {
                Main.util.sendMessage(player, MessagePath.NoPermission.getPath());
                return false;
            }
            FileConfiguration settings = Main.settings.getConfig();
            String databaseSource = settings.getBoolean("mysql.enable") ? "mysql" : "sqlite";
            if (!Main.getInstance.ds.isRunning()) {
                ArrayList<String> messageList = new ArrayList<String>();
                messageList.add("&7&m-----&r&c\u25cf&7&m-----&r &c&l&olight&7Economy &7&m-----&r&c\u25cf&7&m-----&r");
                messageList.add(" ");
                messageList.add("&8\u25cf &4Database has errors. &7Please check the log! SQL Mode: &c" + databaseSource);
                messageList.add("&8\u25cf &7Bukkit Version: &c" + Main.getInstance.getServer().getBukkitVersion());
                messageList.add("&8\u25cf &7Economy Version: &c" + Main.getInstance.getDescription().getVersion());
                messageList.add(" ");
                messageList.add("&7&m-----&r&c\u25cf&7&m-----&r &7&m-----&r&c\u25cf&7\u23fa&c\u25cf&7&m-----&r &7&m-----&r&c\u25cf&7&m-----&r");
                Main.util.sendMessageList(player, messageList);
                return false;
            }
            long startTime = System.currentTimeMillis();
            MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
            CompletableFuture<HashMap<String, Double>> test = moneyTableAsync.getPlayersBalanceList();
            int tableSize = test.get().size();
            long endTime = System.currentTimeMillis();
            long queryDuration = endTime - startTime;
            int totalConnections = Main.getInstance.ds.getHikariPoolMXBean().getTotalConnections();
            int activeConnections = Main.getInstance.ds.getHikariPoolMXBean().getActiveConnections();
            int pendingThreads = Main.getInstance.ds.getHikariPoolMXBean().getThreadsAwaitingConnection();
            int idleConnections = Main.getInstance.ds.getHikariPoolMXBean().getIdleConnections();
            if (Main.economyImplementer.hasAccount(player.getName())) {
                ArrayList<String> messageList = new ArrayList<String>();
                Main.messages.getConfig().getStringList("debugMessage").forEach(singleLine -> {
                    singleLine = singleLine.replace("#databaseSource#", databaseSource).replace("#totalConnections#", String.valueOf(totalConnections)).replace("#activeConnections#", String.valueOf(activeConnections)).replace("#pendingThreads#", String.valueOf(pendingThreads)).replace("#tableSize#", String.valueOf(tableSize)).replace("#queryDuration#", String.valueOf(queryDuration)).replace("#bukkitVersion#", Main.getInstance.getServer().getBukkitVersion()).replace("#ecoVersion#", Main.getInstance.getDescription().getVersion());
                    messageList.add((String)singleLine);
                });
                Main.util.sendMessageList(player, messageList);
                return false;
            }
            Main.util.sendMessage(player, "&4Database connection test failed. &7Please check the log for more information");
            return false;
        }
        Main.util.sendMessage(player, MessagePath.WrongCommand.getPath().replace("#command#", this.getSyntax()));
        return false;
    }
}

