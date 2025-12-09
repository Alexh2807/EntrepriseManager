/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.console;

import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PluginReloadConsole
extends SubCommand {
    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "Reloads the plugin throw the console";
    }

    @Override
    public String getSyntax() {
        return "/eco reload";
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("reload")) {
                Main.messages.reloadConfig("messages.yml");
                Main.titles.reloadConfig("titles.yml");
                Main.bankLevelMenu.reloadConfig("bank-level.yml");
                Main.bankMenu.reloadConfig("bank-menu.yml");
                Main.voucher.reloadConfig("voucher.yml");
                Main.lose.reloadConfig("lose.yml");
                Main.settings.reloadConfig("settings.yml");
                Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Successfully reloaded the configs");
                return false;
            }
            Bukkit.getConsoleSender().sendMessage("This command does not exist. Please try /eco reload");
            return false;
        }
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Wrong command. Please use /eco reload");
        return false;
    }
}

