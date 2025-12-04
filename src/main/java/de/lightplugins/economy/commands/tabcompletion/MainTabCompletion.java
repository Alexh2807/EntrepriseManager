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

import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public class MainTabCompletion
implements TabCompleter {
    public List<String> onTabComplete(CommandSender sender, Command command, String s, String[] args) {
        Player player = (Player)sender;
        if (args.length == 1) {
            ArrayList<String> arguments = new ArrayList<String>();
            arguments.add("help");
            if (player.hasPermission("lighteconomy.admin.command.reload")) {
                arguments.add("reload");
            }
            if (player.hasPermission("lighteconomy.admin.command.debug")) {
                arguments.add("debug");
            }
            return arguments;
        }
        return null;
    }
}

