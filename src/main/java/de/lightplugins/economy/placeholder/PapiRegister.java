/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.expansion.PlaceholderExpansion
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.jetbrains.annotations.NotNull
 */
package de.lightplugins.economy.placeholder;

import de.lightplugins.economy.database.querys.BankTableAsync;
import de.lightplugins.economy.database.querys.MoneyTableAsync;
import de.lightplugins.economy.master.Main;
import de.lightplugins.economy.utils.BankLevelSystem;
import de.lightplugins.economy.utils.Sorter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

public class PapiRegister
extends PlaceholderExpansion {
    public boolean canRegister() {
        return true;
    }

    @NotNull
    public String getIdentifier() {
        return "lighteconomy";
    }

    @NotNull
    public String getAuthor() {
        return "lightPlugins";
    }

    @NotNull
    public String getVersion() {
        return "5.0.0";
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, String params) {
        CompletableFuture<Number> completableFuture;
        BankTableAsync bankTableAsync;
        String empty;
        String name;
        Map.Entry<String, Double> top;
        int i;
        int baltopAmount;
        TreeMap<String, Double> list;
        HashMap<String, Double> map;
        CompletableFuture<HashMap<String, Double>> futureMap;
        ArrayList<String> exclude;
        FileConfiguration settings = Main.settings.getConfig();
        if (params.contains("moneytop_")) {
            MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
            exclude = new ArrayList<>(Main.settings.getConfig().getStringList("settings.baltop-exclude"));
            futureMap = moneyTableAsync.getPlayersBalanceList();
            try {
                map = futureMap.get();
                for (String playername : exclude) {
                    map.remove(playername);
                }
                list = new Sorter(map).get();
                baltopAmount = Main.settings.getConfig().getInt("settings.baltop-amount-of-players");
                for (i = 0; i < baltopAmount; ++i) {
                    try {
                        top = list.pollFirstEntry();
                        name = top.getKey();
                        if (!params.equalsIgnoreCase("moneytop_" + (i + 1))) continue;
                        String message = Main.colorTranslation.hexTranslation(settings.getString("settings.top-placeholder-format"));
                        return message.replace("#number#", String.valueOf(i + 1)).replace("#name#", name).replace("#amount#", String.valueOf(Main.util.finalFormatDouble(top.getValue()))).replace("#currency#", Main.util.getCurrency(top.getValue()));
                    } catch (Exception e) {
                        empty = Main.settings.getConfig().getString("settings.top-placeholder-not-set");
                        String message = Main.colorTranslation.hexTranslation(settings.getString("settings.top-placeholder-format"));
                        return message.replace("#number#", String.valueOf(i + 1)).replace("#name#", empty != null ? empty : "-").replace("#amount#", "0.00").replace("#currency#", Main.util.getCurrency(0.0));
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        if (params.contains("banktop_")) {
            bankTableAsync = new BankTableAsync(Main.getInstance);
            exclude = new ArrayList<>(Main.settings.getConfig().getStringList("settings.baltop-exclude"));
            futureMap = bankTableAsync.getPlayersBalanceList();
            try {
                map = futureMap.get();
                for (String playername : exclude) {
                    map.remove(playername);
                }
                list = new Sorter(map).get();
                baltopAmount = Main.settings.getConfig().getInt("settings.baltop-amount-of-players");
                for (i = 0; i < baltopAmount; ++i) {
                    try {
                        top = list.pollFirstEntry();
                        name = top.getKey();
                        if (!params.equalsIgnoreCase("banktop_" + (i + 1))) continue;
                        String message = Main.colorTranslation.hexTranslation(settings.getString("settings.top-placeholder-format"));
                        return message.replace("#number#", String.valueOf(i + 1)).replace("#name#", name).replace("#amount#", String.valueOf(Main.util.finalFormatDouble(top.getValue()))).replace("#currency#", Main.util.getCurrency(top.getValue()));
                    } catch (Exception e) {
                        empty = Main.settings.getConfig().getString("settings.top-placeholder-not-set");
                        String message = Main.colorTranslation.hexTranslation(settings.getString("settings.top-placeholder-format"));
                        return message.replace("#number#", String.valueOf(i + 1)).replace("#name#", empty != null ? empty : "-").replace("#amount#", "0.00").replace("#currency#", Main.util.getCurrency(0.0));
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        if (params.equalsIgnoreCase("money")) {
            double amount = Main.util.fixDouble(Main.economyImplementer.getBalance(player.getName()));
            return Main.util.formatDouble(amount);
        }
        if (params.equalsIgnoreCase("currency_singular")) {
            return Main.economyImplementer.currencyNameSingular();
        }
        if (params.equalsIgnoreCase("currency_plural")) {
            return Main.economyImplementer.currencyNamePlural();
        }
        if (params.equalsIgnoreCase("bank_balance")) {
            bankTableAsync = new BankTableAsync(Main.getInstance);
            CompletableFuture<Double> bankBalanceFuture = bankTableAsync.playerBankBalance(player.getName());
            try {
                return Main.util.finalFormatDouble(bankBalanceFuture.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        if (params.equalsIgnoreCase("bank_current_level")) {
            bankTableAsync = new BankTableAsync(Main.getInstance);
            CompletableFuture<Integer> bankLevelFuture = bankTableAsync.playerCurrentBankLevel(player.getName());
            try {
                return String.valueOf(bankLevelFuture.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        if (params.equalsIgnoreCase("bank_max_level")) {
            FileConfiguration config = Main.bankLevelMenu.getConfig();
            int maxLevelViaConfig = 0;
            for (int i2 = 0; i2 < config.getConfigurationSection("levels").getKeys(false).size(); ++i2) {
                ++maxLevelViaConfig;
            }
            return String.valueOf(maxLevelViaConfig);
        }
        if (params.equalsIgnoreCase("bank_limit_balance")) {
            BankLevelSystem bankLevelSystem = new BankLevelSystem(Main.getInstance);
            return String.valueOf(bankLevelSystem.getLimitByLevel(player.getUniqueId()));
        }
        if (params.equalsIgnoreCase("money_short")) {
            double amount = Main.util.fixDouble(Main.economyImplementer.getBalance(player.getName()));
            String formatting = "undefined";
            double formattedAmount = 0.0;
            if (amount > 999.99) {
                formatting = "k";
                formattedAmount = amount / 1000.0;
            }
            if (amount > 999999.99) {
                formatting = "m";
                formattedAmount = amount / 1000000.0;
            }
            if (amount > 9.9999999999E8) {
                formatting = "b";
                formattedAmount = amount / 1.0E9;
            }
            if (amount > 9.9999999999999E11) {
                formatting = "t";
                formattedAmount = amount / 1.0E12;
            }
            if (amount < 1000.0) {
                return Main.util.finalFormatDouble(amount);
            }
            String configFormatting = settings.getString("settings.shortPlaceholderFormat");
            if (configFormatting == null) {
                return "settings.yml error";
            }
            return Main.colorTranslation.hexTranslation(configFormatting.replace("#amount#", Main.util.finalFormatDouble(formattedAmount)).replace("#identifier#", formatting).replace("#currency#", Main.util.getCurrency(formattedAmount)));
        }
        if (params.equalsIgnoreCase("bank_balance_short")) {
            bankTableAsync = new BankTableAsync(Main.getInstance);
            CompletableFuture<Double> bankBalanceShortFuture = bankTableAsync.playerBankBalance(player.getName());
            try {
                double amount = bankBalanceShortFuture.get();
                String formatting = "undefined";
                double formattedAmount = 0.0;
                if (amount > 999.99) {
                    formatting = "k";
                    formattedAmount = amount / 1000.0;
                }
                if (amount > 999999.99) {
                    formatting = "m";
                    formattedAmount = amount / 1000000.0;
                }
                if (amount > 9.9999999999E8) {
                    formatting = "b";
                    formattedAmount = amount / 1.0E9;
                }
                if (amount > 9.9999999999999E11) {
                    formatting = "t";
                    formattedAmount = amount / 1.0E12;
                }
                if (amount < 1000.0) {
                    return Main.util.finalFormatDouble(amount);
                }
                String configFormatting = settings.getString("settings.shortPlaceholderFormat");
                if (configFormatting == null) {
                    return "settings.yml error";
                }
                return Main.colorTranslation.hexTranslation("test" + configFormatting.replace("#amount#", Main.util.finalFormatDouble(formattedAmount)).replace("#identifier#", formatting).replace("#currency#", Main.util.getCurrency(formattedAmount)));
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        if (params.equalsIgnoreCase("money_all")) {
            bankTableAsync = new BankTableAsync(Main.getInstance);
            MoneyTableAsync moneyTableAsync = new MoneyTableAsync(Main.getInstance);
            CompletableFuture<Double> completableFuture2 = bankTableAsync.playerBankBalance(player.getName());
            CompletableFuture<Double> completableFuture1 = moneyTableAsync.playerBalance(player.getName());
            try {
                double allTheMoney = completableFuture2.get() + completableFuture1.get();
                return Main.util.finalFormatDouble(allTheMoney);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        if (params.equalsIgnoreCase("money_decimals")) {
            // empty if block
        }
        return null;
    }
}

