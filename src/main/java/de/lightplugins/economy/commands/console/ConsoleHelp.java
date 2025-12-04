/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.console;

import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class ConsoleHelp
extends SubCommand {
    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Shows the help syntax from console";
    }

    @Override
    public String getSyntax() {
        return "/eco help";
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("help")) {
                Bukkit.getConsoleSender().sendMessage("\n  \u00a7r_      _____ _____ _    _ _______ \u00a7c______ _____ ____  _   _  ____  __  ____     __\n \u00a7r| |    |_   _/ ____| |  | |__   __\u00a7c|  ____/ ____/ __ \\| \\ | |/ __ \\|  \\/  \\ \\   / /\n \u00a7r| |      | || |  __| |__| |  | |  \u00a7c| |__ | |   | |  | |  \\| | |  | | \\  / |\\ \\_/ / \n \u00a7r| |      | || | |_ |  __  |  | |  \u00a7c|  __|| |   | |  | | . ` | |  | | |\\/| | \\   /  \n \u00a7r| |____ _| || |__| | |  | |  | |  \u00a7c| |___| |___| |__| | |\\  | |__| | |  | |  | |   \n \u00a7r|______|_____\\_____|_|  |_|  |_|  \u00a7c|______\\_____\\____/|_| \\_|\\____/|_|  |_|  |_|\n\n" + ChatColor.RESET + "      Available console commands:\n\n      \u00a7ceco help \u00a7r- show the command list\n      \u00a7ceco open [playername] \u00a7r- opens the bank menu for target\n      \u00a7ceco add [playername] [amount] \u00a7r- add a certant amount of money to player\n      \u00a7ceco remove [playername] [amount] \u00a7r- remove a certant amount of money to player\n      \u00a7ceco set [playername] [amount] \u00a7r- set a certant amount of money to player\n      \u00a7ceco reset [playername] \u00a7r- delete a player in the database. The player will be kicked and must rejoin\n      \u00a7ceco reload \u00a7r- reloads the configs\n\n");
                return false;
            }
            Bukkit.getConsoleSender().sendMessage("This command does not exist. Please try /eco reload");
            return false;
        }
        Bukkit.getConsoleSender().sendMessage("\u00a7r[light\u00a7cEconomy\u00a7r] Wrong command. Please use /eco reload");
        return false;
    }
}

