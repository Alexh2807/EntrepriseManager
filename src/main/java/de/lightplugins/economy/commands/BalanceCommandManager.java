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

import de.lightplugins.economy.database.querys.MoneyTableAsync;
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
import org.bukkit.entity.Player;

public class BalanceCommandManager
implements CommandExecutor {
    private final ArrayList<SubCommand> subCommands = new ArrayList();
    public Main plugin;

    public ArrayList<SubCommand> getSubCommands() {
        return this.subCommands;
    }

    public BalanceCommandManager(Main plugin) {
        this.plugin = plugin;
    }

    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player)sender;
            if (args.length > 0) {
                for (int i = 0; i < this.subCommands.size(); ++i) {
                    if (!args[0].equalsIgnoreCase(this.getSubCommands().get(i).getName())) continue;
                    try {
                        if (!this.getSubCommands().get(i).perform(player, args)) continue;
                        Main.debugPrinting.sendInfo("MainSubCommand " + Arrays.toString(args) + " successfully executed by " + player.getName());
                        continue;
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException("", e);
                    }
                }
            } else {
                MoneyTableAsync moneyTableAsync = new MoneyTableAsync(this.plugin);
                moneyTableAsync.playerBalance(player.getName()).thenAccept(balance -> {
                    if (balance != null) {
                        double currentBalance = balance;
                        Main.util.sendMessage(player, MessagePath.MoneyBalance.getPath().replace("#balance#", Main.util.finalFormatDouble(currentBalance)).replace("#currency#", Main.economyImplementer.currencyNameSingular()));
                    } else {
                        player.sendMessage(Main.colorTranslation.hexTranslation(MessagePath.Prefix.getPath() + MessagePath.PlayerNotFound.getPath()));
                    }
                });
            }
            if (args.length == 1) {
                if (!player.hasPermission(PermissionPath.MoneyOther.getPerm())) {
                    Main.util.sendMessage(player, MessagePath.NoPermission.getPath());
                    return false;
                }
                MoneyTableAsync moneyTableAsync = new MoneyTableAsync(this.plugin);
                moneyTableAsync.playerBalance(args[0]).thenAccept(balance -> {
                    if (balance != null) {
                        Main.util.sendMessage(player, MessagePath.MoneyBalanceOther.getPath().replace("#target#", args[0]).replace("#balance#", Main.util.finalFormatDouble((double)balance)).replace("#currency#", Main.util.getCurrency((double)balance)));
                    } else {
                        player.sendMessage(Main.colorTranslation.hexTranslation(MessagePath.Prefix.getPath() + MessagePath.PlayerNotFound.getPath()));
                    }
                });
            }
        }
        return false;
    }
}

