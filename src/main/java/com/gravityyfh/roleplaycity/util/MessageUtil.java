package com.gravityyfh.roleplaycity.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * FIX BASSE #15: Utilitaire pour l'envoi de messages formatés
 *
 * Réduit la duplication de code pour l'envoi de messages avec couleurs et icônes.
 * Plus de 700 occurrences dans le code peuvent bénéficier de cette classe.
 *
 * Avantages:
 * - Cohérence visuelle dans tous les messages
 * - Réduction de la duplication (700+ occurrences)
 * - Facilite la modification du format des messages
 * - Support multi-lignes et broadcast
 */
public class MessageUtil {

    // Préfixes avec icônes
    private static final String PREFIX_ERROR = ChatColor.RED + "❌ ";
    private static final String PREFIX_SUCCESS = ChatColor.GREEN + "✅ ";
    private static final String PREFIX_WARNING = ChatColor.YELLOW + "⚠ ";
    private static final String PREFIX_INFO = ChatColor.GRAY + "ℹ ";

    // === MESSAGES AVEC ICÔNES ===

    /**
     * Envoie un message d'erreur (rouge avec ❌)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX_ERROR + message);
    }

    /**
     * Envoie un message de succès (vert avec ✅)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void success(CommandSender sender, String message) {
        sender.sendMessage(PREFIX_SUCCESS + message);
    }

    /**
     * Envoie un message d'avertissement (jaune avec ⚠)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void warning(CommandSender sender, String message) {
        sender.sendMessage(PREFIX_WARNING + message);
    }

    /**
     * Envoie un message d'information (gris avec ℹ)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void info(CommandSender sender, String message) {
        sender.sendMessage(PREFIX_INFO + message);
    }

    // === MESSAGES COLORÉS SIMPLES ===

    /**
     * Envoie un message rouge (sans icône)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void red(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.RED + message);
    }

    /**
     * Envoie un message vert (sans icône)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void green(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GREEN + message);
    }

    /**
     * Envoie un message jaune (sans icône)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void yellow(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.YELLOW + message);
    }

    /**
     * Envoie un message gris (sans icône)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void gray(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GRAY + message);
    }

    /**
     * Envoie un message doré (sans icône)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void gold(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + message);
    }

    /**
     * Envoie un message aqua (sans icône)
     *
     * @param sender Destinataire du message
     * @param message Message à envoyer
     */
    public static void aqua(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.AQUA + message);
    }

    /**
     * Envoie un message avec une couleur personnalisée
     *
     * @param sender Destinataire du message
     * @param color Couleur du message
     * @param message Message à envoyer
     */
    public static void colored(CommandSender sender, ChatColor color, String message) {
        sender.sendMessage(color + message);
    }

    // === MESSAGES MULTI-LIGNES ===

    /**
     * Envoie plusieurs messages d'erreur
     *
     * @param sender Destinataire des messages
     * @param messages Messages à envoyer
     */
    public static void errors(CommandSender sender, String... messages) {
        for (String message : messages) {
            error(sender, message);
        }
    }

    /**
     * Envoie plusieurs messages de succès
     *
     * @param sender Destinataire des messages
     * @param messages Messages à envoyer
     */
    public static void successes(CommandSender sender, String... messages) {
        for (String message : messages) {
            success(sender, message);
        }
    }

    /**
     * Envoie plusieurs messages d'information
     *
     * @param sender Destinataire des messages
     * @param messages Messages à envoyer
     */
    public static void infos(CommandSender sender, String... messages) {
        for (String message : messages) {
            info(sender, message);
        }
    }

    /**
     * Envoie plusieurs lignes colorées
     *
     * @param sender Destinataire des messages
     * @param color Couleur des messages
     * @param messages Messages à envoyer
     */
    public static void coloredLines(CommandSender sender, ChatColor color, String... messages) {
        for (String message : messages) {
            colored(sender, color, message);
        }
    }

    // === BROADCAST ===

    /**
     * Broadcast un message d'erreur à plusieurs joueurs
     *
     * @param players Destinataires du message
     * @param message Message à envoyer
     */
    public static void broadcastError(Collection<? extends Player> players, String message) {
        players.forEach(p -> error(p, message));
    }

    /**
     * Broadcast un message de succès à plusieurs joueurs
     *
     * @param players Destinataires du message
     * @param message Message à envoyer
     */
    public static void broadcastSuccess(Collection<? extends Player> players, String message) {
        players.forEach(p -> success(p, message));
    }

    /**
     * Broadcast un message d'information à plusieurs joueurs
     *
     * @param players Destinataires du message
     * @param message Message à envoyer
     */
    public static void broadcastInfo(Collection<? extends Player> players, String message) {
        players.forEach(p -> info(p, message));
    }

    /**
     * Broadcast un message coloré à plusieurs joueurs
     *
     * @param players Destinataires du message
     * @param color Couleur du message
     * @param message Message à envoyer
     */
    public static void broadcastColored(Collection<? extends Player> players, ChatColor color, String message) {
        players.forEach(p -> colored(p, color, message));
    }

    // === TRADUCTION CODES COULEUR ===

    /**
     * Traduit les codes couleur alternatifs (&) en ChatColor
     *
     * @param message Message avec codes &
     * @return Message avec codes couleur traduits
     */
    public static String colorize(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Traduit et envoie un message avec codes couleur alternatifs
     *
     * @param sender Destinataire du message
     * @param message Message avec codes &
     */
    public static void colorizeAndSend(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }

    // === FORMATAGE SPÉCIAL ===

    /**
     * Crée un séparateur visuel
     *
     * @param color Couleur du séparateur
     * @param length Longueur du séparateur
     * @return Chaîne de séparation
     */
    public static String separator(ChatColor color, int length) {
        return color + "═".repeat(length);
    }

    /**
     * Envoie un séparateur visuel
     *
     * @param sender Destinataire
     * @param color Couleur du séparateur
     * @param length Longueur du séparateur
     */
    public static void sendSeparator(CommandSender sender, ChatColor color, int length) {
        sender.sendMessage(separator(color, length));
    }

    /**
     * Crée un titre encadré
     *
     * @param title Titre
     * @param color Couleur du cadre
     * @return Titre formaté
     */
    public static String boxedTitle(String title, ChatColor color) {
        int length = ChatColor.stripColor(title).length() + 4;
        String border = "═".repeat(length);
        return color + "╔" + border + "╗\n" +
               color + "║ " + ChatColor.BOLD + title + ChatColor.RESET + color + " ║\n" +
               color + "╚" + border + "╝";
    }

    /**
     * Envoie un titre encadré
     *
     * @param sender Destinataire
     * @param title Titre
     * @param color Couleur du cadre
     */
    public static void sendBoxedTitle(CommandSender sender, String title, ChatColor color) {
        sender.sendMessage(boxedTitle(title, color));
    }
}
