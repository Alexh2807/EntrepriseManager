/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 * 
 * Could not load the following classes:
 *  me.clip.placeholderapi.PlaceholderAPI
 *  org.bukkit.Bukkit
 *  org.bukkit.Sound
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 *  org.bukkit.scheduler.BukkitTask
 */
package de.lightplugins.economy.utils;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.lightplugins.economy.enums.MessagePath;
import de.lightplugins.economy.enums.PluginMessagePath;
import de.lightplugins.economy.master.Main;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.logging.Level;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class Util {
    public String languagePlayer() {
        return Main.settings.getConfig().getString("settings.commandSyntaxTranslation.player");
    }

    public String languageAmount() {
        return Main.settings.getConfig().getString("settings.commandSyntaxTranslation.amount");
    }

    public String languageTarget() {
        return Main.settings.getConfig().getString("settings.commandSyntaxTranslation.amount");
    }

    public String getCurrency(double amount) {
        if (amount == 1.0) {
            return Main.economyImplementer.currencyNameSingular();
        }
        return Main.economyImplementer.currencyNamePlural();
    }

    public void sendMessage(Player player, String message) {
        String prefix = MessagePath.Prefix.getPath();
        message = PlaceholderAPI.setPlaceholders((Player)player, (String)message);
        player.sendMessage(Main.colorTranslation.hexTranslation(prefix + message));
    }

    public void sendMessageList(Player player, List<String> list) {
        for (String s : list) {
            s = PlaceholderAPI.setPlaceholders((Player)player, (String)s);
            player.sendMessage(Main.colorTranslation.hexTranslation(s));
        }
    }

    public double fixDouble(double numberToFix) {
        BigDecimal bd = new BigDecimal(numberToFix).setScale(2, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public boolean isNumber(String number) {
        try {
            Double dummy = Double.parseDouble(number);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public String formatDouble(double numberToFormat) {
        boolean internationalDecimals = Main.settings.getConfig().getBoolean("settings.internationalDecimals");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        FileConfiguration config = Main.settings.getConfig();
        if (internationalDecimals) {
            symbols = new DecimalFormatSymbols(Locale.US);
        }
        if (config.getBoolean("settings.currencyWithoutDeciamlPlaces")) {
            BigDecimal bd = new BigDecimal(numberToFormat).setScale(0, RoundingMode.DOWN);
            bd = bd.stripTrailingZeros();
            DecimalFormat decimalFormat = new DecimalFormat("#,###", symbols);
            return decimalFormat.format(bd);
        }
        DecimalFormat decimalFormat = new DecimalFormat("#,##0.00", symbols);
        return decimalFormat.format(numberToFormat);
    }

    public String finalFormatDouble(double numberToRound) {
        return this.formatDouble(this.fixDouble(numberToRound));
    }

    public void countUp(final Player player, final double endValue, final String upperLine, final String lowerLine, final String upperLineFinal, final String lowerLineFinal) {
        double startValue = endValue * 0.05;
        FileConfiguration config = Main.settings.getConfig();
        final Sound countUpSound = Sound.valueOf((String)Objects.requireNonNull(config.getString("settings.count-up-sound")).toUpperCase());
        final Sound countFinishSound = Sound.valueOf((String)Objects.requireNonNull(config.getString("settings.final-count-sound")).toUpperCase());
        BigDecimal bd2 = new BigDecimal(startValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bd = new BigDecimal(endValue).setScale(2, RoundingMode.HALF_UP);
        final double volume = config.getDouble("settings.volume");
        final double pitch = config.getDouble("settings.pitch");
        final double roundedSetPoint = bd.doubleValue();
        final double[] roundedCountMin = new double[]{bd2.doubleValue()};
        BukkitTask task = new BukkitRunnable(){

            public void run() {
                DecimalFormat formatter;
                if (roundedCountMin[0] < roundedSetPoint) {
                    BigDecimal bd3 = BigDecimal.valueOf(roundedCountMin[0]).setScale(2, RoundingMode.HALF_UP);
                    formatter = new DecimalFormat("#,##0.00");
                    String roundedOutput = formatter.format(bd3);
                    roundedCountMin[0] = roundedCountMin[0] + (0.01 + roundedSetPoint - roundedCountMin[0]) / 2.0 / 2.0;
                    player.sendTitle(upperLine.replace("#amount#", roundedOutput), lowerLine.replace("#amount#", roundedOutput), 0, 20, 20);
                    player.playSound(player.getLocation(), countUpSound, (float)volume, (float)pitch);
                }
                if (roundedCountMin[0] >= endValue) {
                    BigDecimal bd4 = new BigDecimal(roundedSetPoint).setScale(2, RoundingMode.HALF_UP);
                    formatter = new DecimalFormat("#,##0.00");
                    String roundedSetPointOutput = formatter.format(bd4);
                    player.sendTitle(upperLineFinal.replace("#amount#", roundedSetPointOutput), lowerLineFinal.replace("#amount#", roundedSetPointOutput), 0, 20, 20);
                    player.playSound(player.getLocation(), countFinishSound, (float)volume, (float)pitch);
                    this.cancel();
                }
            }
        }.runTaskTimer(Main.getInstance.asPlugin(), 0L, 1L);
    }

    public boolean isInventoryEmpty(Player player) {
        return player.getInventory().firstEmpty() != -1;
    }

    public double subtractPercentage(double originalValue, double percentage) {
        Bukkit.getLogger().log(Level.WARNING, "TEST 1 " + originalValue + " - " + percentage);
        if (percentage < 0.0 || percentage > 100.0) {
            throw new IllegalArgumentException("Percentage must be between 0 and 100");
        }
        return percentage / 100.0 * originalValue;
    }

    public boolean checkPercentage(double percent) {
        if (percent < 0.0 || percent > 100.0) {
            throw new IllegalArgumentException("Percent value must be between 0 and 100");
        }
        Random random = new Random();
        double randomPercent = random.nextDouble() * 100.0;
        return randomPercent <= percent;
    }

    public void sendMessageThrowBungeeNetwork(Player sender, String targetName, String message) {
        if (!Main.getInstance.isBungee) {
            return;
        }
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF(PluginMessagePath.PAY.getType());
        out.writeUTF(targetName);
        out.writeUTF(Main.colorTranslation.hexTranslation(message));
        sender.sendPluginMessage(Main.getInstance.asPlugin(), "BungeeCord", out.toByteArray());
    }
}

