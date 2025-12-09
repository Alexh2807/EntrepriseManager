/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.PlaceholderAPI
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.money;

import de.lightplugins.economy.database.querys.MoneyTableAsync;
import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.Sorter;
import de.lightplugins.economy.utils.SubCommand;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class MoneyTopCommand
extends SubCommand {
    @Override
    public String getName() {
        return "top";
    }

    @Override
    public String getDescription() {
        return "Shows the riches player on the server";
    }

    @Override
    public String getSyntax() {
        return "/money top";
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("top")) {
                if (!player.hasPermission(PermissionPath.MoneyTop.getPerm())) {
                    Main.util.sendMessage(player, MessagePath.NoPermission.getPath());
                    return false;
                }
                FileConfiguration settings = Main.settings.getConfig();
                FileConfiguration message = Main.messages.getConfig();
                MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
                ArrayList<String> exclude = new ArrayList<>(settings.getStringList("settings.baltop-exclude"));
                CompletableFuture<HashMap<String, Double>> futureMap = moneyTableAsync.getPlayersBalanceList();
                HashMap<String, Double> map = futureMap.get();
                for (String playername : exclude) {
                    map.remove(playername);
                }
                TreeMap<String, Double> list = new Sorter(map).get();
                CompletableFuture<HashMap<String, Double>> test = moneyTableAsync.getPlayersBalanceList();
                HashMap<String, Double> allPlayers = test.get();
                double allServerMoney = 0.0;
                for (Double single : allPlayers.values()) {
                    allServerMoney += single.doubleValue();
                }
                for (String header : message.getStringList("moneyTopHeader")) {
                    player.sendMessage(Main.colorTranslation.hexTranslation(header.replace("#overall#", Main.util.finalFormatDouble(allServerMoney))).replace("#currency#", Main.util.getCurrency(allServerMoney)));
                }
                int baltopAmount = settings.getInt("settings.baltop-amount-of-players");
                for (int i = 0; i < baltopAmount; ++i) {
                    try {
                        Map.Entry<String, Double> top = list.pollFirstEntry();
                        String name = top.getKey();
                        Player offlinePlayer = Bukkit.getPlayer((String)name);
                        String confMessage = Objects.requireNonNull(message.getString("moneyTopFormat")).replace("#number#", String.valueOf(i + 1)).replace("#name#", name).replace("#amount#", String.valueOf(Main.util.finalFormatDouble(top.getValue()))).replace("#currency#", Main.util.getCurrency(top.getValue()));
                        String finalMessage = PlaceholderAPI.setPlaceholders((OfflinePlayer)offlinePlayer, (String)confMessage);
                        player.sendMessage(Main.colorTranslation.hexTranslation(finalMessage));
                        continue;
                    } catch (Exception top) {
                        // empty catch block
                    }
                }
                for (String footer : message.getStringList("moneyTopFooter")) {
                    String finalMessage = PlaceholderAPI.setPlaceholders((Player)player, (String)footer);
                    player.sendMessage(Main.colorTranslation.hexTranslation(finalMessage));
                }
            }
        } else {
            Main.util.sendMessage(player, MessagePath.WrongCommand.getPath().replace("#command#", this.getSyntax()));
            return false;
        }
        return false;
    }
}

