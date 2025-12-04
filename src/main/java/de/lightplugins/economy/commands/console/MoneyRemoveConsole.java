/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.EconomyResponse
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.console;

import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.ExecutionException;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class MoneyRemoveConsole
extends SubCommand {
    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "remove money throw console";
    }

    @Override
    public String getSyntax() {
        return "/eco remove [playername] [amount]";
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("remove")) {
                String target = args[1];
                try {
                    EconomyResponse economyResponse;
                    double amount = Double.parseDouble(args[2]);
                    String currency = Main.economyImplementer.currencyNameSingular();
                    if (!Main.economyImplementer.hasAccount(target)) {
                        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] The Target does not have an account or the name is wrong!");
                        return false;
                    }
                    if (amount < 0.0) {
                        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] You can add only positiv numbers!");
                        return true;
                    }
                    if (!Main.economyImplementer.has(target, amount)) {
                        amount = Main.economyImplementer.getBalance(target);
                    }
                    if ((economyResponse = Main.economyImplementer.withdrawPlayer(target, amount)).transactionSuccess()) {
                        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully removed " + args[2] + " " + currency + " from " + target);
                        return true;
                    }
                    Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Something went wrong. Please try it again");
                } catch (NumberFormatException e) {
                    Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Please use a valid number and try it again.");
                    return false;
                }
            }
            Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Wrong command. Please use /eco remove [playername] [amount]");
            return false;
        }
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Wrong command. Please use /eco remove [playername] [amount]");
        return false;
    }
}

