/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.main;

import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.ExecutionException;
import org.bukkit.entity.Player;

public class ReloadCommand
extends SubCommand {
    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getDescription() {
        return "reloads the plugin";
    }

    @Override
    public String getSyntax() {
        return "/le reload";
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length != 1) {
            Main.util.sendMessage(player, MessagePath.WrongCommand.getPath());
            return false;
        }
        // Accepter si le joueur a la permission OU s'il est OP
        if (!player.hasPermission(PermissionPath.Reload.getPerm()) && !player.isOp()) {
            Main.util.sendMessage(player, MessagePath.NoPermission.getPath());
            return false;
        }
        Main.messages.reloadConfig("messages.yml");
        Main.titles.reloadConfig("titles.yml");
        Main.bankLevelMenu.reloadConfig("bank-level.yml");
        Main.bankMenu.reloadConfig("bank-menu.yml");
        Main.voucher.reloadConfig("voucher.yml");
        Main.lose.reloadConfig("lose.yml");
        Main.settings.reloadConfig("settings.yml");
        Main.util.sendMessage(player, MessagePath.Reload.getPath());
        return false;
    }
}

