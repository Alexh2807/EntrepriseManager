/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 */
package de.lightplugins.economy.listener;

import de.lightplugins.economy.database.querys.BankTableAsync;
import de.lightplugins.economy.database.querys.MoneyTableAsync;
import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.BankLevelSystem;
import de.lightplugins.economy.utils.Sounds;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class BankListener
implements Listener {
    private Main plugin;

    public BankListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority=EventPriority.NORMAL)
    private void onChatBankWithdraw(AsyncPlayerChatEvent e) {
        block11: {
            Player chatter = e.getPlayer();
            String message = e.getMessage();
            if (!this.plugin.bankWithdrawValue.contains(chatter)) {
                return;
            }
            e.setCancelled(true);
            if (message.equalsIgnoreCase("cancel")) {
                this.plugin.bankWithdrawValue.remove(chatter);
                return;
            }
            this.plugin.bankWithdrawValue.remove(chatter);
            MoneyTableAsync moneyTable = new MoneyTableAsync(this.plugin);
            BankTableAsync bankTable = new BankTableAsync(this.plugin);
            Sounds sounds = new Sounds();
            try {
                double bankAmount;
                double pocketAmount;
                double amount = Double.parseDouble(message);
                CompletableFuture<Double> futurePocket = moneyTable.playerBalance(chatter.getName());
                CompletableFuture<Double> futureBank = bankTable.playerBankBalance(chatter.getName());
                try {
                    pocketAmount = futurePocket.get();
                    bankAmount = futureBank.get();
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
                if (amount <= 0.0) {
                    Main.util.sendMessage(chatter, MessagePath.OnlyPositivNumbers.getPath());
                    sounds.soundOnFailure(chatter);
                    this.plugin.bankDepositValue.remove(chatter);
                    return;
                }
                if (amount > bankAmount) {
                    Main.util.sendMessage(chatter, MessagePath.BankWithdrawNotEnough.getPath());
                    sounds.soundOnFailure(chatter);
                    return;
                }
                if (!(amount <= bankAmount)) break block11;
                CompletableFuture<Boolean> completableFuture = moneyTable.setMoney(chatter.getName(), pocketAmount + amount);
                CompletableFuture<Boolean> completableFuture1 = bankTable.setBankMoney(chatter.getName(), bankAmount - amount);
                try {
                    if (completableFuture1.get().booleanValue() && completableFuture.get().booleanValue()) {
                        Main.util.sendMessage(chatter, MessagePath.BankWithdrawSuccessfully.getPath().replace("#amount#", String.valueOf(amount)).replace("#currency#", Main.util.getCurrency(amount)));
                        sounds.soundOnSuccess(chatter);
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            } catch (NumberFormatException ex) {
                Main.util.sendMessage(chatter, MessagePath.NotANumber.getPath());
                sounds.soundOnFailure(chatter);
            }
        }
    }

    @EventHandler(priority=EventPriority.NORMAL)
    private void onChatBankDeposit(AsyncPlayerChatEvent e) {
        Player chatter = e.getPlayer();
        String message = e.getMessage();
        if (!this.plugin.bankDepositValue.contains(chatter)) {
            return;
        }
        e.setCancelled(true);
        if (message.equalsIgnoreCase("cancel") && this.plugin.bankDepositValue.contains(chatter)) {
            this.plugin.bankDepositValue.remove(chatter);
            return;
        }
        this.plugin.bankDepositValue.remove(chatter);
        try {
            double bankAmount;
            double pocketAmount;
            double amount = Double.parseDouble(message);
            BankLevelSystem bankLevelSystem = new BankLevelSystem(this.plugin);
            Sounds sounds = new Sounds();
            MoneyTableAsync moneyTable = new MoneyTableAsync(this.plugin);
            BankTableAsync bankTable = new BankTableAsync(this.plugin);
            CompletableFuture<Double> futurePocket = moneyTable.playerBalance(chatter.getName());
            CompletableFuture<Double> futureBank = bankTable.playerBankBalance(chatter.getName());
            try {
                pocketAmount = futurePocket.get();
                bankAmount = futureBank.get();
            } catch (InterruptedException | ExecutionException ex) {
                throw new RuntimeException(ex);
            }
            if (amount <= 0.0) {
                Main.util.sendMessage(chatter, MessagePath.OnlyPositivNumbers.getPath());
                sounds.soundOnFailure(chatter);
                this.plugin.bankDepositValue.remove(chatter);
                return;
            }
            if (bankAmount == bankLevelSystem.getLimitByLevel(chatter.getUniqueId())) {
                Main.util.sendMessage(chatter, MessagePath.BankDepositNotPossible.getPath());
                sounds.soundOnFailure(chatter);
                this.plugin.bankDepositValue.remove(chatter);
                return;
            }
            if (amount > bankLevelSystem.getLimitByLevel(chatter.getUniqueId())) {
                Main.util.sendMessage(chatter, MessagePath.BankDepositOverLimit.getPath());
                sounds.soundOnFailure(chatter);
                this.plugin.bankDepositValue.remove(chatter);
                return;
            }
            if (bankAmount + amount >= bankLevelSystem.getLimitByLevel(chatter.getUniqueId())) {
                Main.util.sendMessage(chatter, MessagePath.BankDepositOverLimit.getPath());
                sounds.soundOnFailure(chatter);
                this.plugin.bankDepositValue.remove(chatter);
                return;
            }
            double currentBankBalance = bankAmount;
            if (amount <= pocketAmount) {
                CompletableFuture<Boolean> completableFuture = moneyTable.setMoney(chatter.getName(), pocketAmount - amount);
                CompletableFuture<Boolean> completableFuture1 = bankTable.setBankMoney(chatter.getName(), currentBankBalance + amount);
                try {
                    if (completableFuture.get().booleanValue() && completableFuture1.get().booleanValue()) {
                        Main.util.sendMessage(chatter, MessagePath.BankDepositSuccessfully.getPath().replace("#amount#", String.valueOf(amount)).replace("#currency#", Main.util.getCurrency(amount)));
                        sounds.soundOnSuccess(chatter);
                        this.plugin.bankDepositValue.remove(chatter);
                        return;
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
            if (amount > pocketAmount) {
                Main.util.sendMessage(chatter, MessagePath.BankDepositNotEnough.getPath());
                sounds.soundOnFailure(chatter);
            }
        } catch (NumberFormatException exception) {
            Main.util.sendMessage(chatter, MessagePath.NotANumber.getPath());
        }
    }
}

