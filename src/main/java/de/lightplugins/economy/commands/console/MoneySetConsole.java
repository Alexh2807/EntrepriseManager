/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.console;

import de.lightplugins.economy.database.querys.MoneyTableAsync;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class MoneySetConsole
extends SubCommand {
    @Override
    public String getName() {
        return "set";
    }

    @Override
    public String getDescription() {
        return "set money throw console";
    }

    @Override
    public String getSyntax() {
        return "/eco set [playername] [amount]";
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set")) {
                String target = args[1];
                String currency = Main.economyImplementer.currencyNameSingular();
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (!Main.economyImplementer.hasAccount(target)) {
                        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] The Target does not have an account or the name is wrong! + " + args[1]);
                        return false;
                    }
                    if (amount < 0.0) {
                        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] You can set only positiv numbers!");
                        return true;
                    }
                    if (amount > 9.9999999999999E11) {
                        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] The Amount exceeds the Limit of 999,999,999,999.99");
                        return false;
                    }
                    MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
                    CompletableFuture<Boolean> completableFuture = moneyTableAsync.setMoney(target, amount);
                    if (completableFuture.get().booleanValue()) {
                        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully set " + args[2] + " " + currency + " to " + target);
                        return true;
                    }
                    Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Something went wrong. Please try it again");
                } catch (NumberFormatException e) {
                    Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Please use a valid number and try it again.");
                    return false;
                }
            }
            Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Wrong command. Please use /eco set [playername] [amount]");
            return false;
        }
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Wrong command. Please use /eco set [playername] [amount]");
        return false;
    }
}

