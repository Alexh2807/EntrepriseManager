/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands;

import de.lightplugins.economy.commands.bank.BankAddCommand;
import de.lightplugins.economy.commands.bank.BankMenuCommand;
import de.lightplugins.economy.commands.bank.BankRemoveCommand;
import de.lightplugins.economy.commands.bank.BankSetCommand;
import de.lightplugins.economy.commands.bank.BankSetLevelCommand;
import de.lightplugins.economy.commands.bank.BankShowCommand;
import de.lightplugins.economy.commands.bank.BankTopCommand;
import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.inventories.BankMainMenu;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class BankCommandManager
implements CommandExecutor {
    private final ArrayList<SubCommand> subCommands = new ArrayList();
    public Main plugin;

    public ArrayList<SubCommand> getSubCommands() {
        return this.subCommands;
    }

    public BankCommandManager(Main plugin) {
        this.plugin = plugin;
        this.subCommands.add(new BankMenuCommand());
        this.subCommands.add(new BankAddCommand());
        this.subCommands.add(new BankSetCommand());
        this.subCommands.add(new BankSetLevelCommand());
        this.subCommands.add(new BankRemoveCommand());
        this.subCommands.add(new BankShowCommand());
        this.subCommands.add(new BankTopCommand());
    }

    public boolean onCommand(CommandSender sender, Command command, String s, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player)sender;

            boolean contains = false;
            FileConfiguration config = Main.settings.getConfig();
            if (!config.getBoolean("settings.enable-bank-feature")) {
                Main.util.sendMessage(player, MessagePath.BankDisabled.getPath());
                return false;
            }
            if (args.length > 0) {
                for (int i = 0; i < this.subCommands.size(); ++i) {
                    if (!args[0].equalsIgnoreCase(this.getSubCommands().get(i).getName())) continue;
                    try {
                        this.getSubCommands().get(i).perform(player, args);
                        contains = true;
                        Main.debugPrinting.sendInfo("MainSubCommand " + Arrays.toString(args) + " successfully executed by " + player.getName());
                        return true;
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException("Something went wrong", e);
                    }
                }
            } else {
                if (player.hasPermission(PermissionPath.BankOpen.getPerm())) {
                    // Utiliser le nouveau flux (Création -> Chargement -> Banque)
                    com.gravityyfh.roleplaycity.util.BankUtils.openBankFlow(player);
                    return true;
                }
                Main.util.sendMessage(player, MessagePath.NoPermission.getPath());
                return true;
            }
        }
        return true;
    }
}

