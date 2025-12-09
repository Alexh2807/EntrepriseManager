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

public class MoneyReset
extends SubCommand {
    @Override
    public String getName() {
        return "delete";
    }

    @Override
    public String getDescription() {
        return "delete a player from the database";
    }

    @Override
    public String getSyntax() {
        return "/eco reset [PLAYERNAME]";
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length != 2) {
            Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Wrong command. Please use /eco reset [PLAYERNAME]");
            return false;
        }
        String target = args[1];
        if (!Main.economyImplementer.hasAccount(target)) {
            Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] The Target does not have an account and cant be deleted!");
            return false;
        }
        MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
        CompletableFuture<Boolean> delete = moneyTableAsync.deleteAccount(target);
        if (delete.get().booleanValue()) {
            Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully deleted user " + target);
            Player tar = Bukkit.getPlayer((String)target);
            if (tar == null) {
                return false;
            }
            tar.kickPlayer("\u00a77[lightEconomy] \u00a7cYour economy account was deleted. \n\u00a7cPlease connect again to the server");
        } else {
            Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Cannot delete user " + target + ". Something went wrong");
        }
        return false;
    }
}

