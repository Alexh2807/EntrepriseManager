/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.console;

import de.lightplugins.economy.inventories.BankMainMenu;
import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BankOpenConsole
extends SubCommand {
    @Override
    public String getName() {
        return "bank";
    }

    @Override
    public String getDescription() {
        return "bank commands from console";
    }

    @Override
    public String getSyntax() {
        return "eco bank open <player>";
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length == 3) {
            Player target = Bukkit.getPlayer((String)args[2]);
            if (args[1].equalsIgnoreCase("open")) {
                if (target == null) {
                    Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] The Player was not found on this server");
                    return false;
                }
                if (target.isOnline()) {
                    BankMainMenu.INVENTORY.open(target);
                    return false;
                }
                return false;
            }
            Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Wrong command. Please use /eco bank open PLAYERNAME");
            return false;
        }
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Wrong command. Please use /eco bank open PLAYERNAME");
        return false;
    }
}

