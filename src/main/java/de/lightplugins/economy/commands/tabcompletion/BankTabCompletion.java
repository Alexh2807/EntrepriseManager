/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.tabcompletion;

import de.lightplugins.economy.enums.PermissionPath;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class BankTabCompletion
implements TabCompleter {
    public List<String> onTabComplete(CommandSender sender, Command command, String s, String[] args) {
        Player player = (Player)sender;
        if (args.length == 1) {
            ArrayList<String> arguments = new ArrayList<String>();
            if (player.hasPermission(PermissionPath.BankOpenOther.getPerm())) {
                arguments.add("open");
            }
            if (player.hasPermission(PermissionPath.BankAdd.getPerm())) {
                arguments.add("add");
            }
            if (player.hasPermission(PermissionPath.BankRemove.getPerm())) {
                arguments.add("remove");
            }
            if (player.hasPermission(PermissionPath.BankSetLevel.getPerm())) {
                arguments.add("level");
            }
            if (player.hasPermission(PermissionPath.BankShow.getPerm())) {
                arguments.add("show");
            }
            return arguments;
        }
        if (args.length == 2) {
            ArrayList<String> arguments = new ArrayList<String>();
            if (args[0].equalsIgnoreCase("level")) {
                arguments.add("set");
            }
            return arguments;
        }
        return null;
    }
}

