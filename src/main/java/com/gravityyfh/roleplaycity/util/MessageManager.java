package com.gravityyfh.roleplaycity.util;

import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FIX BASSE #20-22: Système de gestion des messages avec i18n
 *
 * Centralise tous les messages du plugin avec support multi-langues,
 * formatage uniforme et templates réutilisables.
 */
public class MessageManager {

    private final Plugin plugin;
    private FileConfiguration messages;
    private String currentLanguage;

    // Préfixes uniformes
    private String prefixInfo;
    private String prefixSuccess;
    private String prefixWarning;
    private String prefixError;
    private String prefixDebug;

    public MessageManager(Plugin plugin) {
        this.plugin = plugin;
        this.currentLanguage = plugin.getConfig().getString("system.language", "fr");
        loadMessages();
        loadPrefixes();
    }

    /**
     * Charge le fichier de messages pour la langue configurée
     */
    private void loadMessages() {
        File messagesFolder = new File(plugin.getDataFolder(), "messages");
        if (!messagesFolder.exists()) {
            messagesFolder.mkdirs();
        }

        File messagesFile = new File(messagesFolder, "messages_" + currentLanguage + ".yml");

        // Copier le fichier par défaut si inexistant
        if (!messagesFile.exists()) {
            saveDefaultMessages(messagesFile);
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);
        plugin.getLogger().info("Messages chargés: " + currentLanguage);
    }

    /**
     * Sauvegarde les messages par défaut depuis les ressources
     */
    private void saveDefaultMessages(File file) {
        try {
            InputStream inputStream = plugin.getResource("messages_" + currentLanguage + ".yml");
            if (inputStream == null) {
                // Fallback sur français si langue non disponible
                inputStream = plugin.getResource("messages_fr.yml");
            }
            if (inputStream != null) {
                Files.copy(inputStream, file.toPath());
                inputStream.close();
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de créer le fichier de messages: " + e.getMessage());
        }
    }

    /**
     * Charge les préfixes de messages
     */
    private void loadPrefixes() {
        prefixInfo = translateColors(messages.getString("prefixes.info", "&7[&bInfo&7]&f"));
        prefixSuccess = translateColors(messages.getString("prefixes.success", "&7[&a✓&7]&f"));
        prefixWarning = translateColors(messages.getString("prefixes.warning", "&7[&e⚠&7]&f"));
        prefixError = translateColors(messages.getString("prefixes.error", "&7[&c✗&7]&f"));
        prefixDebug = translateColors(messages.getString("prefixes.debug", "&7[&dDebug&7]&f"));
    }

    /**
     * Récupère un message traduit
     */
    public String getMessage(String key, Object... args) {
        String message = messages.getString(key);
        if (message == null) {
            plugin.getLogger().warning("Message manquant: " + key);
            return "&c[Message manquant: " + key + "]";
        }

        // Remplacer les placeholders {0}, {1}, etc.
        for (int i = 0; i < args.length; i++) {
            message = message.replace("{" + i + "}", String.valueOf(args[i]));
        }

        return translateColors(message);
    }

    /**
     * Envoie un message d'information
     */
    public void sendInfo(CommandSender sender, String key, Object... args) {
        sender.sendMessage(prefixInfo + " " + getMessage(key, args));
    }

    /**
     * Envoie un message de succès
     * FIX BASSE #23: Ajout son de confirmation
     */
    public void sendSuccess(CommandSender sender, String key, Object... args) {
        sender.sendMessage(prefixSuccess + " " + getMessage(key, args));
        if (sender instanceof Player) {
            ((Player) sender).playSound(((Player) sender).getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    /**
     * Envoie un message d'avertissement
     */
    public void sendWarning(CommandSender sender, String key, Object... args) {
        sender.sendMessage(prefixWarning + " " + getMessage(key, args));
        if (sender instanceof Player) {
            ((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        }
    }

    /**
     * Envoie un message d'erreur
     * FIX BASSE #21: Messages d'erreur améliorés avec contexte
     */
    public void sendError(CommandSender sender, String key, Object... args) {
        sender.sendMessage(prefixError + " " + getMessage(key, args));
        if (sender instanceof Player) {
            ((Player) sender).playSound(((Player) sender).getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    /**
     * Envoie un message de debug (si debug activé)
     */
    public void sendDebug(CommandSender sender, String message) {
        if (plugin.getConfig().getBoolean("system.debug-mode", false)) {
            sender.sendMessage(prefixDebug + " " + translateColors(message));
        }
    }

    /**
     * FIX BASSE #23: Envoie une demande de confirmation avec boutons interactifs
     * Utilise InteractiveMessage au lieu de commandes à taper
     *
     * @deprecated Utilisez ConfirmationManager.requestConfirmation() pour les confirmations
     */
    @Deprecated
    public void sendConfirmation(Player player, String actionKey, String confirmCommand, Object... args) {
        // Ancienne méthode conservée pour compatibilité
        // Utilise maintenant InteractiveMessage
        String question = getMessage(actionKey, args);

        new InteractiveMessage()
            .emptyLine()
            .separator(net.md_5.bungee.api.ChatColor.GOLD)
            .text("⚠ CONFIRMATION REQUISE", net.md_5.bungee.api.ChatColor.YELLOW, true, false)
            .newLine()
            .separator(net.md_5.bungee.api.ChatColor.GOLD)
            .emptyLine()
            .text(question, net.md_5.bungee.api.ChatColor.WHITE)
            .emptyLine()
            .button("✓ CONFIRMER", "/rc:confirm", "Cliquez pour confirmer l'action", net.md_5.bungee.api.ChatColor.GREEN)
            .spaces(3)
            .button("✗ ANNULER", "/rc:cancel", "Cliquez pour annuler", net.md_5.bungee.api.ChatColor.RED)
            .emptyLine()
            .text("Cette confirmation expire dans 30 secondes", net.md_5.bungee.api.ChatColor.GRAY, false, true)
            .newLine()
            .separator(net.md_5.bungee.api.ChatColor.GOLD)
            .send(player);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
    }

    /**
     * Envoie une demande d'input avec InteractiveMessage
     *
     * @deprecated Utilisez ChatInputListener.requestInput() pour les saisies
     */
    @Deprecated
    public void sendInputRequest(Player player, String promptKey, Object... args) {
        String prompt = getMessage(promptKey, args);

        new InteractiveMessage()
            .emptyLine()
            .separator(net.md_5.bungee.api.ChatColor.AQUA)
            .text("✎ SAISIE REQUISE", net.md_5.bungee.api.ChatColor.AQUA, true, false)
            .newLine()
            .separator(net.md_5.bungee.api.ChatColor.AQUA)
            .emptyLine()
            .text(prompt, net.md_5.bungee.api.ChatColor.WHITE)
            .emptyLine()
            .text("→ ", net.md_5.bungee.api.ChatColor.YELLOW, true, false)
            .text("Entrez votre réponse dans le chat", net.md_5.bungee.api.ChatColor.GRAY, false, true)
            .newLine()
            .emptyLine()
            .button("✗ ANNULER", "/rc:cancelinput", "Cliquez pour annuler la saisie", net.md_5.bungee.api.ChatColor.RED)
            .emptyLine()
            .text("Expire dans 60 secondes", net.md_5.bungee.api.ChatColor.DARK_GRAY, false, true)
            .newLine()
            .separator(net.md_5.bungee.api.ChatColor.AQUA)
            .send(player);

        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
    }

    /**
     * Envoie un titre au joueur
     * FIX BASSE #24: Titres GUI améliorés
     */
    public void sendTitle(Player player, String titleKey, String subtitleKey, Object... args) {
        String title = getMessage(titleKey, args);
        String subtitle = subtitleKey != null ? getMessage(subtitleKey, args) : "";

        player.sendTitle(
            translateColors(title),
            translateColors(subtitle),
            10, 70, 20
        );
    }

    /**
     * Envoie un message d'action bar
     */
    public void sendActionBar(Player player, String key, Object... args) {
        // Utiliser la méthode Spigot pour l'action bar
        player.spigot().sendMessage(
            net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
            net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                translateColors(getMessage(key, args))
            )
        );
    }

    /**
     * Récupère une liste de messages
     */
    public List<String> getMessageList(String key, Object... args) {
        List<String> list = messages.getStringList(key);
        if (list.isEmpty()) {
            plugin.getLogger().warning("Liste de messages manquante: " + key);
            return List.of("&c[Message manquant: " + key + "]");
        }

        // Remplacer les placeholders
        return list.stream()
            .map(line -> {
                String result = line;
                for (int i = 0; i < args.length; i++) {
                    result = result.replace("{" + i + "}", String.valueOf(args[i]));
                }
                return translateColors(result);
            })
            .toList();
    }

    /**
     * Envoie une liste de messages (header + lignes + footer)
     */
    public void sendMessageBox(CommandSender sender, String headerKey, List<String> lines, String footerKey) {
        sender.sendMessage(translateColors(getMessage(headerKey)));
        lines.forEach(sender::sendMessage);
        if (footerKey != null) {
            sender.sendMessage(translateColors(getMessage(footerKey)));
        }
    }

    /**
     * Formate un montant d'argent selon la locale
     */
    public String formatMoney(double amount) {
        return String.format("%.2f€", amount);
    }

    /**
     * Formate un nombre avec séparateurs de milliers
     */
    public String formatNumber(long number) {
        return String.format("%,d", number);
    }

    /**
     * Formate un pourcentage
     */
    public String formatPercent(double percent) {
        return String.format("%.1f%%", percent);
    }

    /**
     * Traduit les codes couleur & en ChatColor
     */
    private String translateColors(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Recharge les messages depuis le fichier
     */
    public void reload() {
        this.currentLanguage = plugin.getConfig().getString("system.language", "fr");
        loadMessages();
        loadPrefixes();
        plugin.getLogger().info("Messages rechargés: " + currentLanguage);
    }

    /**
     * Change la langue
     */
    public void setLanguage(String language) {
        this.currentLanguage = language;
        loadMessages();
        loadPrefixes();
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    // Getters pour les préfixes (utiles pour messages custom)
    public String getPrefixInfo() { return prefixInfo; }
    public String getPrefixSuccess() { return prefixSuccess; }
    public String getPrefixWarning() { return prefixWarning; }
    public String getPrefixError() { return prefixError; }
}
