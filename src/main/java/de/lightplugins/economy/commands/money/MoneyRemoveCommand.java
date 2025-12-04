/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.EconomyResponse
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.commands.money;

import de.lightplugins.economy.database.querys.MoneyTableAsync;
import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.ExecutionException;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;

public class MoneyRemoveCommand
extends SubCommand {
    public Main plugin;

    public MoneyRemoveCommand(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getDescription() {
        return "Remove a specified amount of money";
    }

    @Override
    public String getSyntax() {
        return "/money remove " + Main.util.languagePlayer() + " " + Main.util.languageAmount();
    }

    @Override
    public boolean perform(Player player, String[] args) {
        if (args.length != 3) {
            Main.util.sendMessage(player, MessagePath.WrongCommand.getPath().replace("#command#", this.getSyntax()));
            return true;
        }
        if (!Main.economyImplementer.hasAccount(args[1])) {
            Main.util.sendMessage(player, MessagePath.PlayerNotExists.getPath());
            return false;
        }
        // Accepter si le joueur a la permission OU s'il est OP
        if (!player.hasPermission(PermissionPath.MoneyRemove.getPerm()) && !player.isOp()) {
            Main.util.sendMessage(player, MessagePath.NoPermission.getPath());
            return false;
        }
        try {
            double amount = Double.parseDouble(args[2]);
            if (amount == 0.0) {
                Main.util.sendMessage(player, MessagePath.NotZero.getPath());
                return true;
            }
            if (amount < 0.0) {
                Main.util.sendMessage(player, MessagePath.OnlyPositivNumbers.getPath());
                return true;
            }
            try {
                EconomyResponse moneyRemove;
                MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
                double currentBalance = moneyTableAsync.playerBalance(args[1]).get();
                if (amount >= currentBalance) {
                    amount = currentBalance;
                }
                if ((moneyRemove = Main.economyImplementer.withdrawPlayer(args[1], amount)).transactionSuccess()) {
                    Main.util.sendMessage(player, MessagePath.MoneyRemovePlayer.getPath().replace("#currency#", Main.util.getCurrency(amount)).replace("#target#", args[1]).replace("#amount#", Main.util.formatDouble(amount)));
                    return true;
                }
                Main.debugPrinting.sendError(moneyRemove.errorMessage);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Something went wrong on player remove money", e);
            }
        } catch (NumberFormatException e) {
            Main.util.sendMessage(player, MessagePath.NotANumber.getPath());
            return true;
        }
        return false;
    }
}

