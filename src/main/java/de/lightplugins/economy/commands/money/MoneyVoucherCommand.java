/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.EconomyResponse
 *  org.bukkit.entity.Item
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 */
package de.lightplugins.economy.commands.money;

import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PermissionPath;
import de.lightplugins.economy.items.Voucher;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.SubCommand;
import java.util.concurrent.ExecutionException;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

public class MoneyVoucherCommand
extends SubCommand {
    @Override
    public String getName() {
        return "voucher";
    }

    @Override
    public String getDescription() {
        return "Create a physical Voucher";
    }

    @Override
    public String getSyntax() {
        return "/le voucher create";
    }

    @Override
    public boolean perform(final Player player, final String[] args) throws ExecutionException, InterruptedException {
        if (args.length != 3) {
            Main.util.sendMessage(player, MessagePath.WrongCommand.getPath().replace("#command#", this.getSyntax()));
            return false;
        }
        if (!player.hasPermission(PermissionPath.CreateVoucher.getPerm())) {
            Main.util.sendMessage(player, MessagePath.NoPermission.getPath());
            return false;
        }
        if (!Main.voucher.getConfig().getBoolean("voucher.enable")) {
            Main.util.sendMessage(player, MessagePath.VoucherDisabled.getPath());
            return false;
        }
        if (args[1].equalsIgnoreCase("create")) {
            new BukkitRunnable(){

                public void run() {
                    double playerBalance = Main.economyImplementer.getBalance(player.getName());
                    double minValue = Main.voucher.getConfig().getDouble("voucher.min-value");
                    double maxValue = Main.voucher.getConfig().getDouble("voucher.max-value");
                    try {
                        double itemValue = Double.parseDouble(args[2]);
                        if (itemValue < 0.0) {
                            Main.util.sendMessage(player, MessagePath.OnlyPositivNumbers.getPath());
                            return;
                        }
                        if (itemValue > maxValue) {
                            Main.util.sendMessage(player, MessagePath.VoucherMaxValue.getPath().replace("#max-value#", String.valueOf(maxValue)).replace("#currency#", Main.util.getCurrency(maxValue)));
                            return;
                        }
                        if (itemValue < minValue) {
                            Main.util.sendMessage(player, MessagePath.VoucherMinValue.getPath().replace("#min-value#", String.valueOf(minValue)).replace("#currency#", Main.util.getCurrency(minValue)));
                            return;
                        }
                        if (itemValue > playerBalance) {
                            Main.util.sendMessage(player, MessagePath.NotEnoughtMoney.getPath());
                            return;
                        }
                        EconomyResponse economyResponse = Main.economyImplementer.withdrawPlayer(player.getName(), itemValue);
                        if (!economyResponse.transactionSuccess()) {
                            Main.util.sendMessage(player, MessagePath.TransactionFailed.getPath().replace("#reason#", economyResponse.errorMessage));
                            return;
                        }
                        Voucher voucher = new Voucher();
                        if (Main.util.isInventoryEmpty(player)) {
                            player.getInventory().addItem(new ItemStack[]{voucher.createVoucher(itemValue, player.getName())});
                            Main.util.sendMessage(player, MessagePath.VoucherCreate.getPath().replace("#amount#", String.valueOf(itemValue)).replace("#currency#", Main.util.getCurrency(itemValue)));
                            return;
                        }
                        Item item = player.getWorld().dropItem(player.getLocation(), voucher.createVoucher(itemValue, player.getName()));
                        item.setCustomName(Main.colorTranslation.hexTranslation(Main.voucher.getConfig().getString("voucher.name")).replace("#amount#", args[2]).replace("#currency#", Main.util.getCurrency(itemValue)));
                        item.setCustomNameVisible(true);
                        Main.util.sendMessage(player, MessagePath.VoucherCreate.getPath());
                    } catch (NumberFormatException ex) {
                        Main.util.sendMessage(player, MessagePath.NotANumber.getPath());
                    }
                }
            }.runTaskLater(Main.getInstance.asPlugin(), 10L);
        }
        return false;
    }
}

