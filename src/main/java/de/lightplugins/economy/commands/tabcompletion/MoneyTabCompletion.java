/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 */
package de.lightplugins.economy.commands.tabcompletion;

import de.lightplugins.economy.enums.PermissionPath;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class MoneyTabCompletion
implements TabCompleter {
    public List<String> onTabComplete(CommandSender sender, Command command, String s, String[] args) {
        if (args.length == 1) {
            ArrayList<String> arguments = new ArrayList<String>();
            if (sender.hasPermission(PermissionPath.MoneyAdd.getPerm())) {
                arguments.add("add");
            }
            if (sender.hasPermission(PermissionPath.MoneyAddAll.getPerm())) {
                arguments.add("addall");
            }
            if (sender.hasPermission(PermissionPath.MoneyRemove.getPerm())) {
                arguments.add("remove");
            }
            if (sender.hasPermission(PermissionPath.MoneySet.getPerm())) {
                arguments.add("set");
            }
            if (sender.hasPermission(PermissionPath.MoneyTop.getPerm())) {
                arguments.add("top");
            }
            if (sender.hasPermission(PermissionPath.MoneyOther.getPerm())) {
                arguments.add("show");
            }
            if (sender.hasPermission(PermissionPath.CreateVoucher.getPerm())) {
                arguments.add("voucher create");
            }
            return arguments;
        }
        return null;
    }
}

