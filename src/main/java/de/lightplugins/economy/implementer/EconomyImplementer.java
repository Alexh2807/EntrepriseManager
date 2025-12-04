/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.Economy
 *  net.milkbowl.vault.economy.EconomyResponse
 *  net.milkbowl.vault.economy.EconomyResponse$ResponseType
 *  org.bukkit.Bukkit
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 */
package de.lightplugins.economy.implementer;

import de.lightplugins.economy.database.querys.BankTableAsync;
import de.lightplugins.economy.database.querys.MoneyTableAsync;
import de.lightplugins.economy.master.Main;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class EconomyImplementer
implements Economy {
    public Main plugin = Main.getInstance;

    public boolean isEnabled() {
        return true;
    }

    public String getName() {
        return this.plugin.getName();
    }

    public boolean hasBankSupport() {
        return false;
    }

    public int fractionalDigits() {
        return -1;
    }

    public String format(double v) {
        return Main.util.formatDouble(v) + " " + Main.util.getCurrency(v);
    }

    public String currencyNamePlural() {
        FileConfiguration settings = Main.settings.getConfig();
        return settings.getString("settings.currency-name-plural");
    }

    public String currencyNameSingular() {
        FileConfiguration settings = Main.settings.getConfig();
        return settings.getString("settings.currency-name-singular");
    }

    public boolean hasAccount(String s) {
        MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
        // Utiliser isPlayerAccount pour vérifier si le compte existe vraiment
        CompletableFuture<Boolean> accountExistsFuture = moneyTableAsync.isPlayerAccount(s);
        try {
            return accountExistsFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            // Si erreur, vérifier avec l'ancienne méthode
            CompletableFuture<Double> balanceFuture = moneyTableAsync.playerBalance(s);
            try {
                Double balance = balanceFuture.get();
                return balance != null;
            } catch (InterruptedException | ExecutionException e2) {
                throw new RuntimeException("Something went wrong", e2);
            }
        }
    }

    public boolean hasAccount(OfflinePlayer offlinePlayer) {
        return this.hasAccount(offlinePlayer.getName());
    }

    public boolean hasAccount(String s, String s1) {
        return this.hasAccount(s);
    }

    public boolean hasAccount(OfflinePlayer offlinePlayer, String s) {
        return this.hasAccount(offlinePlayer.getName());
    }

    public double getBalance(String s) {
        MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
        BankTableAsync bankTableAsync = new BankTableAsync(Main.getInstance);
        CompletableFuture<Double> balance = moneyTableAsync.playerBalance(s);
        CompletableFuture<Boolean> isPlayer = moneyTableAsync.isPlayerAccount(s);
        FileConfiguration settings = Main.settings.getConfig();
        boolean bankAsPocket = settings.getBoolean("settings.bankAsPocket");
        try {
            if (bankAsPocket && isPlayer.get().booleanValue()) {
                CompletableFuture<Double> bankBalance = bankTableAsync.playerBankBalance(s);
                return balance.get() + bankBalance.get();
            }
            return balance.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public double getBalance(OfflinePlayer offlinePlayer) {
        return this.getBalance(offlinePlayer.getName());
    }

    public double getBalance(String s, String s1) {
        return this.getBalance(s);
    }

    public double getBalance(OfflinePlayer offlinePlayer, String s) {
        return this.getBalance(offlinePlayer.getName());
    }

    public boolean has(String s, String s1, double v) {
        return this.has(s, v);
    }

    public boolean has(OfflinePlayer offlinePlayer, double v) {
        return this.has(offlinePlayer.getName(), v);
    }

    public boolean has(String s, double v) {
        BankTableAsync bankTableAsync = new BankTableAsync(Main.getInstance);
        MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
        CompletableFuture<Double> bankBalance = bankTableAsync.playerBankBalance(s);
        CompletableFuture<Boolean> isPlayer = moneyTableAsync.isPlayerAccount(s);
        FileConfiguration settings = Main.settings.getConfig();
        boolean bankAsPocket = settings.getBoolean("settings.bankAsPocket");
        if (!bankAsPocket && this.getBalance(s) >= v) {
            return true;
        }
        try {
            double missingAmount;
            double currentBankBalance = 0.0;
            if (isPlayer.get().booleanValue()) {
                currentBankBalance = bankBalance.get();
            }
            if (currentBankBalance >= (missingAmount = v - this.getBalance(s))) {
                return true;
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Something went wrong due to bankAsPocket funtion", e);
        }
        return false;
    }

    public boolean has(OfflinePlayer offlinePlayer, String s, double v) {
        return this.has(offlinePlayer.getName(), v);
    }

    public EconomyResponse withdrawPlayer(String s, double v) {
        MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
        BankTableAsync bankTableAsync = new BankTableAsync(Main.getInstance);
        CompletableFuture<Double> currentPocketBalance = moneyTableAsync.playerBalance(s);
        FileConfiguration titles = Main.titles.getConfig();
        double minTrigger = titles.getDouble("titles.withdraw-wallet.min-trigger-amount");
        String upperTitle = Main.colorTranslation.hexTranslation(titles.getString("titles.withdraw-wallet.counter.upper-line"));
        String lowerTitle = Main.colorTranslation.hexTranslation(titles.getString("titles.withdraw-wallet.counter.lower-line"));
        String upperTitleFinal = Main.colorTranslation.hexTranslation(titles.getString("titles.withdraw-wallet.final.upper-line"));
        String lowerTitleFinal = Main.colorTranslation.hexTranslation(titles.getString("titles.withdraw-wallet.final.lower-line"));
        if (!this.hasAccount(s)) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] The Player does not have an account");
        }
        if (!this.has(s, v)) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] The Player has not enough money");
        }
        if (v < 0.0) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] Cant withdraw negative numbers");
        }
        try {
            double currentBalance = currentPocketBalance.get();
            if (v > currentBalance) {
                CompletableFuture<Double> bankBalance = bankTableAsync.playerBankBalance(s);
                double missingBalance = v - currentBalance;
                double currentBankBalance = bankBalance.get();
                if (currentBankBalance >= missingBalance) {
                    CompletableFuture<Boolean> withdrawFromBank = bankTableAsync.setBankMoney(s, currentBankBalance - missingBalance);
                    CompletableFuture<Boolean> withdrawFromPocket = moneyTableAsync.setMoney(s, 0.0);
                    if (withdrawFromBank.get().booleanValue() && withdrawFromPocket.get().booleanValue()) {
                        Player offlinePlayer = Bukkit.getPlayer((String)s);
                        if (offlinePlayer != null && offlinePlayer.isOnline()) {
                            Player player = offlinePlayer.getPlayer();
                            if (titles.getBoolean("titles.withdraw-wallet.enable") && v >= minTrigger) {
                                Main.util.countUp(player, v, upperTitle, lowerTitle, upperTitleFinal, lowerTitleFinal);
                            }
                        }
                        return new EconomyResponse(v, currentBalance, EconomyResponse.ResponseType.SUCCESS, "[lightEconomy] Successfully withdraw the missing money from lightEconomy bank");
                    }
                }
                return new EconomyResponse(v, currentBalance, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] Something went wrong on withdraw with option bankAsPocket");
            }
            CompletableFuture<Boolean> completableFuture = moneyTableAsync.setMoney(s, currentBalance -= v);
            if (completableFuture.get().booleanValue()) {
                Player offlinePlayer = Bukkit.getPlayer((String)s);
                if (offlinePlayer != null && offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();
                    if (titles.getBoolean("titles.withdraw-wallet.enable") && v >= minTrigger) {
                        Main.util.countUp(player, v, upperTitle, lowerTitle, upperTitleFinal, lowerTitleFinal);
                    }
                }
                return new EconomyResponse(v, currentBalance, EconomyResponse.ResponseType.SUCCESS, "[lightEconomy] Successfully withdraw");
            }
            return new EconomyResponse(v, currentBalance, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] Something went wrong on withdraw");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Something went wrong due to bankAsPocket funtion", e);
        }
    }

    public EconomyResponse withdrawPlayer(OfflinePlayer offlinePlayer, double v) {
        return this.withdrawPlayer(offlinePlayer.getName(), v);
    }

    public EconomyResponse withdrawPlayer(String s, String s1, double v) {
        return this.withdrawPlayer(s, v);
    }

    public EconomyResponse withdrawPlayer(OfflinePlayer offlinePlayer, String s, double v) {
        return this.withdrawPlayer(offlinePlayer.getName(), v);
    }

    public EconomyResponse depositPlayer(String s, double v) {
        MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
        FileConfiguration settings = Main.settings.getConfig();
        double maxPocketBalance = settings.getDouble("settings.max-pocket-balance");
        if (!this.hasAccount(s)) {
            // Créer automatiquement le compte s'il n'existe pas
            org.bukkit.Bukkit.getLogger().info("[EconomyImplementer] Creating account for: " + s);
            try {
                moneyTableAsync.createNewPlayer(s).get();
            } catch (Exception e) {
                return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] Failed to create player account");
            }
        }
        if (v < 0.0) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] Cant deposit negative numbers");
        }
        if (v > maxPocketBalance) {
            return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] The deposit value is to big");
        }
        CompletableFuture<Double> futureBalance = moneyTableAsync.playerBalance(s);
        try {
            Double balanceObj = futureBalance.get();
            if (balanceObj == null) {
                return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] Player account not found in database");
            }
            double currentBalance = balanceObj;
            currentBalance += v;
            if (currentBalance > maxPocketBalance) {
                return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] The player reached the max balance for his pocket");
            }
            CompletableFuture<Boolean> completableFuture = moneyTableAsync.setMoney(s, currentBalance);
            if (completableFuture.get().booleanValue()) {
                FileConfiguration titles = Main.titles.getConfig();
                double minTrigger = titles.getDouble("titles.deposit-wallet.min-trigger-amount");
                String upperTitle = Main.colorTranslation.hexTranslation(titles.getString("titles.deposit-wallet.counter.upper-line"));
                String lowerTitle = Main.colorTranslation.hexTranslation(titles.getString("titles.deposit-wallet.counter.lower-line"));
                String upperTitleFinal = Main.colorTranslation.hexTranslation(titles.getString("titles.deposit-wallet.final.upper-line"));
                String lowerTitleFinal = Main.colorTranslation.hexTranslation(titles.getString("titles.deposit-wallet.final.lower-line"));
                Player offlinePlayer = Bukkit.getPlayer((String)s);
                if (offlinePlayer != null && offlinePlayer.isOnline()) {
                    Player player = offlinePlayer.getPlayer();
                    if (titles.getBoolean("titles.deposit-wallet.enable") && v >= minTrigger) {
                        Main.util.countUp(player, v, upperTitle, lowerTitle, upperTitleFinal, lowerTitleFinal);
                    }
                }
                return new EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.SUCCESS, "[lightEconomy] Successfully deposit");
            }
            return new EconomyResponse(v, currentBalance, EconomyResponse.ResponseType.FAILURE, "[lightEconomy] Something went wrong on deposit");
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public EconomyResponse depositPlayer(OfflinePlayer offlinePlayer, double v) {
        return this.depositPlayer(offlinePlayer.getName(), v);
    }

    public EconomyResponse depositPlayer(String s, String s1, double v) {
        return this.depositPlayer(s, v);
    }

    public EconomyResponse depositPlayer(OfflinePlayer offlinePlayer, String s, double v) {
        return this.depositPlayer(offlinePlayer.getName(), v);
    }

    public EconomyResponse createBank(String s, String s1) {
        return null;
    }

    public EconomyResponse createBank(String s, OfflinePlayer offlinePlayer) {
        return null;
    }

    public EconomyResponse deleteBank(String s) {
        return null;
    }

    public EconomyResponse bankBalance(String s) {
        return null;
    }

    public EconomyResponse bankHas(String s, double v) {
        return null;
    }

    public EconomyResponse bankWithdraw(String s, double v) {
        return null;
    }

    public EconomyResponse bankDeposit(String s, double v) {
        return null;
    }

    public EconomyResponse isBankOwner(String s, String s1) {
        return null;
    }

    public EconomyResponse isBankOwner(String s, OfflinePlayer offlinePlayer) {
        return null;
    }

    public EconomyResponse isBankMember(String s, String s1) {
        return null;
    }

    public EconomyResponse isBankMember(String s, OfflinePlayer offlinePlayer) {
        return null;
    }

    public List<String> getBanks() {
        return null;
    }

    public boolean createPlayerAccount(String s) {
        MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
        CompletableFuture<Boolean> completableFuture = moneyTableAsync.createNewPlayer(s);
        try {
            return completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean createPlayerAccount(OfflinePlayer offlinePlayer) {
        return this.createPlayerAccount(offlinePlayer.getName());
    }

    public boolean createPlayerAccount(String s, String s1) {
        return this.createPlayerAccount(s);
    }

    public boolean createPlayerAccount(OfflinePlayer offlinePlayer, String s) {
        return this.createPlayerAccount(offlinePlayer.getName());
    }
}

