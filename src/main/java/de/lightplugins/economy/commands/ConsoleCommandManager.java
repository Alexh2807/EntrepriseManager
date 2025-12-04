/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.ConsoleCommandSender
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package de.lightplugins.economy.commands;

import de.lightplugins.economy.commands.console.BankOpenConsole;
import de.lightplugins.economy.commands.console.ConsoleHelp;
import de.lightplugins.economy.commands.console.MoneyAddConsole;
import de.lightplugins.economy.commands.console.MoneyGiveConsole;
import de.lightplugins.economy.commands.console.MoneyRemoveConsole;
import de.lightplugins.economy.commands.console.MoneyReset;
import de.lightplugins.economy.commands.console.MoneySetConsole;
import de.lightplugins.economy.commands.console.PluginReloadConsole;
import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ConsoleCommandManager
implements CommandExecutor {
    private final ArrayList<SubCommand> subCommands = new ArrayList();
    public Main plugin;

    public ArrayList<SubCommand> getSubCommands() {
        return this.subCommands;
    }

    public ConsoleCommandManager(Main plugin) {
        this.plugin = plugin;
        this.subCommands.add(new MoneyAddConsole());
        this.subCommands.add(new MoneyRemoveConsole());
        this.subCommands.add(new MoneySetConsole());
        this.subCommands.add(new PluginReloadConsole());
        this.subCommands.add(new MoneyReset());
        this.subCommands.add(new BankOpenConsole());
        this.subCommands.add(new ConsoleHelp());
        this.subCommands.add(new MoneyGiveConsole());
    }

    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, String[] args) {
        if (sender == null) {
            ConsoleCommandManager.$$$reportNull$$$0(0);
        }
        if (command == null) {
            ConsoleCommandManager.$$$reportNull$$$0(1);
        }
        if (s == null) {
            ConsoleCommandManager.$$$reportNull$$$0(2);
        }
        if (sender instanceof ConsoleCommandSender) {
            if (args.length > 0) {
                for (int i = 0; i < this.subCommands.size(); ++i) {
                    if (!args[0].equalsIgnoreCase(this.getSubCommands().get(i).getName())) continue;
                    try {
                        if (!this.getSubCommands().get(i).perform(null, args)) continue;
                        return false;
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException("Something went wrong in executing " + Arrays.toString(args), e);
                    }
                }
            }
        } else {
            if (!sender.hasPermission(PermissionPath.MoneyRemove.getPerm())) {
                Main.util.sendMessage((Player)sender, MessagePath.NoPermission.getPath());
                return false;
            }
            Main.util.sendMessage((Player)sender, MessagePath.OnlyConsole.getPath());
        }
        return false;
    }

    private static /* synthetic */ void $$$reportNull$$$0(int n) {
        Object[] objectArray;
        Object[] objectArray2 = new Object[3];
        switch (n) {
            default: {
                objectArray = objectArray2;
                objectArray2[0] = "sender";
                break;
            }
            case 1: {
                objectArray = objectArray2;
                objectArray2[0] = "command";
                break;
            }
            case 2: {
                objectArray = objectArray2;
                objectArray2[0] = "s";
                break;
            }
        }
        objectArray[1] = "de/lightplugins/economy/commands/ConsoleCommandManager";
        objectArray[2] = "onCommand";
        throw new IllegalArgumentException(String.format("Argument for @NotNull parameter '%s' of %s.%s must not be null", objectArray));
    }
}

