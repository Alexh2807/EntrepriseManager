package com.gravityyfh.roleplaycity.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

/**
 * FIX BASSE #29: Système de logging avec mode debug configurable
 *
 * Permet d'activer des logs détaillés sans recompiler le plugin.
 * Le mode debug peut être activé dans config.yml: system.debug-mode: true
 */
public class DebugLogger {

    private final Plugin plugin;
    private boolean debugEnabled;
    private boolean verboseEnabled;

    public DebugLogger(Plugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Recharge les paramètres depuis la config
     */
    public void reload() {
        this.debugEnabled = plugin.getConfig().getBoolean("system.debug-mode", ConfigDefaults.SYSTEM_DEBUG_MODE);
        this.verboseEnabled = plugin.getConfig().getBoolean("system.verbose-mode", false);

        if (debugEnabled) {
            plugin.getLogger().info("[Debug] Mode debug activé");
        }
        if (verboseEnabled) {
            plugin.getLogger().info("[Debug] Mode verbose activé");
        }
    }

    /**
     * Log debug (uniquement si debug activé)
     */
    public void debug(String message) {
        if (debugEnabled) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    /**
     * Log debug avec catégorie
     */
    public void debug(String category, String message) {
        if (debugEnabled) {
            plugin.getLogger().info(String.format("[DEBUG:%s] %s", category, message));
        }
    }

    /**
     * Log verbose (encore plus détaillé)
     */
    public void verbose(String message) {
        if (verboseEnabled) {
            plugin.getLogger().fine("[VERBOSE] " + message);
        }
    }

    /**
     * Log verbose avec catégorie
     */
    public void verbose(String category, String message) {
        if (verboseEnabled) {
            plugin.getLogger().fine(String.format("[VERBOSE:%s] %s", category, message));
        }
    }

    /**
     * Log une opération de transaction économique
     */
    public void transaction(String type, String entity, double amount, String details) {
        if (debugEnabled) {
            plugin.getLogger().info(String.format(
                "[DEBUG:TRANSACTION] Type=%s Entity=%s Amount=%.2f€ Details=%s",
                type, entity, amount, details
            ));
        }
    }

    /**
     * Log une opération de sauvegarde/chargement
     */
    public void persistence(String operation, String entity, int count, long timeMs) {
        if (debugEnabled) {
            plugin.getLogger().info(String.format(
                "[DEBUG:PERSISTENCE] Op=%s Entity=%s Count=%d Time=%dms",
                operation, entity, count, timeMs
            ));
        }
    }

    /**
     * Log une vérification de permission
     */
    public void permission(String player, String permission, boolean result) {
        if (verboseEnabled) {
            plugin.getLogger().fine(String.format(
                "[VERBOSE:PERMISSION] Player=%s Permission=%s Result=%s",
                player, permission, result
            ));
        }
    }

    /**
     * Log une interaction GUI
     */
    public void gui(String player, String guiName, String action, String item) {
        if (verboseEnabled) {
            plugin.getLogger().fine(String.format(
                "[VERBOSE:GUI] Player=%s GUI=%s Action=%s Item=%s",
                player, guiName, action, item
            ));
        }
    }

    /**
     * Log une erreur avec contexte debug
     */
    public void error(String context, Exception e) {
        plugin.getLogger().log(Level.SEVERE, "[ERROR] " + context, e);

        // En mode debug, afficher la stack trace complète
        if (debugEnabled) {
            e.printStackTrace();
        }
    }

    /**
     * Envoie un message debug à un joueur (admin seulement)
     */
    public void sendDebugToPlayer(CommandSender sender, String message) {
        if (sender.hasPermission("roleplaycity.admin.debug")) {
            sender.sendMessage(ChatColor.GRAY + "[Debug] " + ChatColor.WHITE + message);
        }
    }

    /**
     * Broadcast un message debug à tous les admins en ligne
     */
    public void broadcastDebugToAdmins(String message) {
        if (!debugEnabled) return;

        String formattedMessage = ChatColor.GOLD + "[Debug] " + ChatColor.YELLOW + message;

        Bukkit.getOnlinePlayers().stream()
            .filter(player -> player.hasPermission("roleplaycity.admin.debug"))
            .forEach(player -> player.sendMessage(formattedMessage));
    }

    /**
     * Log un warning conditionnel (uniquement si debug activé)
     */
    public void warnIfDebug(String message) {
        if (debugEnabled) {
            plugin.getLogger().warning("[DEBUG:WARNING] " + message);
        }
    }

    /**
     * Affiche un résumé de l'état du système de debug
     */
    public void printStatus() {
        plugin.getLogger().info("=== État du Système de Debug ===");
        plugin.getLogger().info("  Mode Debug: " + (debugEnabled ? "ACTIVÉ" : "DÉSACTIVÉ"));
        plugin.getLogger().info("  Mode Verbose: " + (verboseEnabled ? "ACTIVÉ" : "DÉSACTIVÉ"));
        plugin.getLogger().info("================================");
    }

    // Getters
    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public boolean isVerboseEnabled() {
        return verboseEnabled;
    }

    // Setters (pour commandes)
    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        plugin.getLogger().info("[Debug] Mode debug " + (enabled ? "activé" : "désactivé"));
    }

    public void setVerboseEnabled(boolean enabled) {
        this.verboseEnabled = enabled;
        plugin.getLogger().info("[Debug] Mode verbose " + (enabled ? "activé" : "désactivé"));
    }
}
