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

import de.lightplugins.economy.commands.money.MoneyAddAllCommand;
import de.lightplugins.economy.commands.money.MoneyAddCommand;
import de.lightplugins.economy.commands.money.MoneyRemoveCommand;
import de.lightplugins.economy.commands.money.MoneySetCommand;
import de.lightplugins.economy.commands.money.MoneyShowCommand;
import de.lightplugins.economy.commands.money.MoneyTopCommand;
import de.lightplugins.economy.commands.money.MoneyVoucherCommand;
import de.lightplugins.economy.database.querys.MoneyTableAsync;
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

public class MoneyCommandManager
implements CommandExecutor {
    private final ArrayList<SubCommand> subCommands = new ArrayList();
    public Main plugin;

    public ArrayList<SubCommand> getSubCommands() {
        return this.subCommands;
    }

    public MoneyCommandManager(Main plugin) {
        this.plugin = plugin;
        this.subCommands.add(new MoneyAddCommand());
        this.subCommands.add(new MoneyRemoveCommand(plugin));
        this.subCommands.add(new MoneySetCommand(plugin));
        this.subCommands.add(new MoneyTopCommand());
        this.subCommands.add(new MoneyShowCommand());
        this.subCommands.add(new MoneyAddAllCommand());
        this.subCommands.add(new MoneyVoucherCommand());
    }

    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        // LOG VISIBLE
        org.bukkit.Bukkit.getLogger().info("[MoneyCommandManager] onCommand called by " + sender.getName() + " with " + args.length + " args: " + java.util.Arrays.toString(args));
        if (sender instanceof Player) {
            ((Player)sender).sendMessage("§e[DEBUG] MoneyCommandManager appelé avec " + args.length + " arguments");
        }

        if (sender instanceof Player) {
            Player player = (Player)sender;
            boolean contains = false;
            if (args.length > 0) {
                org.bukkit.Bukkit.getLogger().info("[MoneyCommandManager] Searching for subcommand: " + args[0] + " in " + this.subCommands.size() + " subcommands");
                for (int i = 0; i < this.subCommands.size(); ++i) {
                    String subCmdName = this.getSubCommands().get(i).getName();
                    org.bukkit.Bukkit.getLogger().info("[MoneyCommandManager] Comparing '" + args[0] + "' with '" + subCmdName + "'");
                    if (!args[0].equalsIgnoreCase(this.getSubCommands().get(i).getName())) continue;
                    org.bukkit.Bukkit.getLogger().info("[MoneyCommandManager] MATCH FOUND! Executing " + subCmdName);
                    try {
                        this.getSubCommands().get(i).perform(player, args);
                        contains = true;
                        Main.debugPrinting.sendInfo("MainSubCommand " + Arrays.toString(args) + " successfully executed by " + player.getName());
                        return true;
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException("Something went wrong while executing MoneycommandManager", e);
                    }
                }
            } else {
                MoneyTableAsync moneyTableAsync = new MoneyTableAsync(this.plugin);
                moneyTableAsync.playerBalance(player.getName()).thenAccept(balance -> {
                    if (balance != null) {
                        double currentBalance = balance;
                        Main.util.sendMessage(player, MessagePath.MoneyBalance.getPath().replace("#balance#", Main.util.finalFormatDouble(currentBalance)).replace("#currency#", Main.util.getCurrency(currentBalance)));
                    } else {
                        player.sendMessage(Main.colorTranslation.hexTranslation(MessagePath.Prefix.getPath() + MessagePath.PlayerNotFound.getPath()));
                    }
                });
                return true;
            }
        }
        return true;
    }
}

