/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.EconomyResponse
 *  org.bukkit.Bukkit
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 *  org.bukkit.scheduler.BukkitTask
 *  org.jetbrains.annotations.NotNull
 */
package de.lightplugins.economy.commands;

import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.master.Main;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class PayCommandMaster
implements CommandExecutor {
    private final List<String> cooldown = new ArrayList<String>();

    public boolean onCommand(final @NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (sender == null) {
            PayCommandMaster.$$$reportNull$$$0(0);
        }
        if (command == null) {
            PayCommandMaster.$$$reportNull$$$0(1);
        }
        if (s == null) {
            PayCommandMaster.$$$reportNull$$$0(2);
        }
        if (args == null) {
            PayCommandMaster.$$$reportNull$$$0(3);
        }
        if (sender instanceof Player) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("toggle")) {
                    if (Main.payToggle.contains(sender.getName())) {
                        Main.payToggle.remove(sender.getName());
                        Main.util.sendMessage((Player)sender, MessagePath.PayEnabled.getPath());
                        return false;
                    }
                    Main.payToggle.add(sender.getName());
                    Main.util.sendMessage((Player)sender, MessagePath.PayDisabled.getPath());
                    return false;
                }
                Main.util.sendMessage((Player)sender, MessagePath.PayWrongCommand.getPath());
                return false;
            }
            if (args.length == 2) {
                if (!sender.hasPermission(PermissionPath.PayCommand.getPerm())) {
                    Main.util.sendMessage((Player)sender, MessagePath.NoPermission.getPath());
                    return false;
                }
                if (this.cooldown.contains(sender.getName())) {
                    Main.util.sendMessage((Player)sender, MessagePath.PayCooldown.getPath());
                    return false;
                }
                String target = args[0];
                if (!Main.util.isNumber(args[1])) {
                    Main.util.sendMessage((Player)sender, MessagePath.NotANumber.getPath());
                    return false;
                }
                double amount = Double.parseDouble(args[1]);
                if (!Main.economyImplementer.hasAccount(target)) {
                    Main.util.sendMessage((Player)sender, MessagePath.PlayerNotExists.getPath());
                    return false;
                }
                if (target.equalsIgnoreCase(sender.getName())) {
                    Main.util.sendMessage((Player)sender, MessagePath.NotYourself.getPath());
                    return false;
                }
                if (amount < 0.0) {
                    Main.util.sendMessage((Player)sender, MessagePath.OnlyPositivNumbers.getPath());
                    return false;
                }
                if (!Main.economyImplementer.has(sender.getName(), amount)) {
                    Main.util.sendMessage((Player)sender, MessagePath.PayFailed.getPath().replace("#reason#", "Not enough Money"));
                    return false;
                }
                if (Main.payToggle.contains(target)) {
                    Main.util.sendMessage((Player)sender, MessagePath.PayFailed.getPath().replace("#reason#", "Target disabled payments"));
                    return false;
                }
                EconomyResponse withdrawExecutor = Main.economyImplementer.withdrawPlayer(sender.getName(), amount);
                EconomyResponse depositTarget = Main.economyImplementer.depositPlayer(target, amount);
                if (withdrawExecutor.transactionSuccess() && depositTarget.transactionSuccess()) {
                    Main.util.sendMessage((Player)sender, MessagePath.PaySenderSuccess.getPath().replace("#amount#", Main.util.formatDouble(amount)).replace("#currency#", Main.economyImplementer.currencyNameSingular()).replace("#target#", target));
                    if (Main.getInstance.isBungee) {
                        Main.util.sendMessageThrowBungeeNetwork((Player)sender, target, MessagePath.PayTargetSuccess.getPath().replace("#amount#", Main.util.formatDouble(amount)).replace("#currency#", Main.economyImplementer.currencyNameSingular()).replace("#sender#", sender.getName()));
                    } else {
                        Player targetPlayer = Bukkit.getPlayer((String)target);
                        if (targetPlayer != null && targetPlayer.isOnline()) {
                            Main.util.sendMessage(Objects.requireNonNull(targetPlayer.getPlayer()), MessagePath.PayTargetSuccess.getPath().replace("#amount#", Main.util.formatDouble(amount)).replace("#currency#", Main.economyImplementer.currencyNameSingular()).replace("#sender#", sender.getName()));
                        }
                    }
                    this.cooldown.add(sender.getName());
                    BukkitTask task = new BukkitRunnable(){
                        final int[] counter = new int[]{0};

                        public void run() {
                            if (this.counter[0] >= 5) {
                                PayCommandMaster.this.cooldown.remove(sender.getName());
                                this.cancel();
                            }
                            this.counter[0] = this.counter[0] + 1;
                        }
                    }.runTaskTimerAsynchronously(Main.getInstance.asPlugin(), 0L, 20L);
                    return false;
                }
                if (withdrawExecutor.transactionSuccess() & !depositTarget.transactionSuccess()) {
                    EconomyResponse moneyRedo = Main.economyImplementer.depositPlayer(sender.getName(), amount);
                    if (moneyRedo.transactionSuccess()) {
                        Main.util.sendMessage((Player)sender, MessagePath.PayFailed.getPath().replace("#reason#", depositTarget.errorMessage));
                        return false;
                    }
                    Main.util.sendMessage((Player)sender, MessagePath.NotHappening.getPath());
                    return false;
                }
                return false;
            }
        }
        Main.util.sendMessage((Player)sender, MessagePath.PayWrongCommand.getPath());
        return false;
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
            case 2: {
                objectArray = objectArray2;
                objectArray2[0] = "s";
                break;
            }
            case 3: {
                objectArray = objectArray2;
                objectArray2[0] = "args";
                break;
            }
        }
        objectArray[1] = "de/lightplugins/economy/commands/PayCommandMaster";
        objectArray[2] = "onCommand";
        throw new IllegalArgumentException(String.format("Argument for @NotNull parameter '%s' of %s.%s must not be null", objectArray));
    }
}

