/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands;

import de.lightplugins.economy.commands.main.DebugCommand;
import de.lightplugins.economy.commands.main.HelpCommand;
import de.lightplugins.economy.commands.main.ReloadCommand;
import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MainCommandManager
implements CommandExecutor {
    private final ArrayList<SubCommand> subCommands = new ArrayList();
    public Main plugin;

    public ArrayList<SubCommand> getSubCommands() {
        return this.subCommands;
    }

    public MainCommandManager(Main plugin) {
        this.plugin = plugin;
        this.subCommands.add(new HelpCommand());
        this.subCommands.add(new ReloadCommand());
        this.subCommands.add(new DebugCommand());
    }

    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player)sender;
            if (args.length > 0) {
                for (int i = 0; i < this.subCommands.size(); ++i) {
                    if (!args[0].equalsIgnoreCase(this.getSubCommands().get(i).getName())) continue;
                    try {
                        this.getSubCommands().get(i).perform(player, args);
                        Main.debugPrinting.sendInfo("MainSubCommand " + Arrays.toString(args) + " successfully executed by " + player.getName());
                        return true;
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException("Something went wrong in executing " + Arrays.toString(args), e);
                    }
                }
            } else {
                Main.util.sendMessage(player, MessagePath.WrongCommand.getPath().replace("#command#", "/le help"));
                return true;
            }
        }
        return true;
    }
}

