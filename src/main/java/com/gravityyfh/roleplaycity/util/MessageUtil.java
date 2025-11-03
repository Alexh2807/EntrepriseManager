package com.gravityyfh.roleplaycity.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public class MessageUtil {

    private MessageUtil() {
        // Utility class
    }

    public static void sendError(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.RED + message);
    }

    public static void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GREEN + message);
    }

    public static void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.YELLOW + message);
    }

    public static void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + message);
    }

    public static String format(String message, Object... args) {
        return String.format(message, args);
    }

    public static String formatMoney(double amount) {
        return String.format("%,.2f€", amount);
    }
}
