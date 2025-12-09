/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.entity.Player
 *  org.jetbrains.annotations.NotNull
 */
package de.lightplugins.economy.commands.tabcompletion;

import de.lightplugins.economy.database.querys.MoneyTableAsync;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.master.Main;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BalanceTabCompletion
implements TabCompleter {
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, String s, String[] args) {
        if (sender == null) {
            BalanceTabCompletion.$$$reportNull$$$0(0);
        }
        if (command == null) {
            BalanceTabCompletion.$$$reportNull$$$0(1);
        }
        Player player = (Player)sender;
        if (args.length == 1) {
            ArrayList<String> arguments;
            if (!player.hasPermission(PermissionPath.MoneyOther.getPerm())) {
                return null;
            }
            MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
            CompletableFuture<HashMap<String, Double>> test = moneyTableAsync.getPlayersBalanceList();
            try {
                HashMap<String, Double> finalPlayerList = test.get();
                arguments = new ArrayList<String>(finalPlayerList.keySet());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
            return arguments;
        }
        return null;
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
        }
        objectArray[1] = "de/lightplugins/economy/commands/tabcompletion/BalanceTabCompletion";
        objectArray[2] = "onTabComplete";
        throw new IllegalArgumentException(String.format("Argument for @NotNull parameter '%s' of %s.%s must not be null", objectArray));
    }
}

