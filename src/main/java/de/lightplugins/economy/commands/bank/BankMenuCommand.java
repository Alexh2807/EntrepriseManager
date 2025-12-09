/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.bank;

import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.inventories.BankMainMenu;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.ExecutionException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BankMenuCommand
extends SubCommand {
    @Override
    public String getName() {
        return "open";
    }

    @Override
    public String getDescription() {
        return "Opens the bank Inventory";
    }

    @Override
    public String getSyntax() {
        return "/bank open " + Main.util.languagePlayer();
    }

    @Override
    public boolean perform(Player player, String[] args) throws ExecutionException, InterruptedException {
        if (args.length == 1) {
            // /bank open - Ouvrir le menu pour soi-même
            BankMainMenu.INVENTORY.open(player);
            return true;
        }

        if (args.length == 2) {
            // /bank open <joueur> - Ouvrir le menu pour un autre joueur (admin)
            if (!player.hasPermission(PermissionPath.BankOpenOther.getPerm()) && !player.isOp()) {
                Main.util.sendMessage(player, MessagePath.NoPermission.getPath());
                return false;
            }
            Player target = Bukkit.getPlayer((String)args[1]);
            if (target == null) {
                Main.debugPrinting.sendWarning("Target player from /bank open [target] not found!");
                Main.util.sendMessage(player, MessagePath.PlayerNotExists.getPath());
                return false;
            }
            BankMainMenu.INVENTORY.open(target);
            return true;
        }

        Main.util.sendMessage(player, MessagePath.WrongCommand.getPath().replace("#command#", this.getSyntax()));
        return false;
    }
}

