package com.gravityyfh.roleplaycity.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Gestionnaire de commandes principales du plugin RoleplayCity
 * Gère les commandes administratives comme /roleplaycity reload
 */
public class RoleplayCityCommandHandler implements CommandExecutor, TabCompleter {

    private final RoleplayCity plugin;

    public RoleplayCityCommandHandler(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Vérifier que c'est bien la commande roleplaycity
        if (!command.getName().equalsIgnoreCase("roleplaycity")) {
            return false;
        }

        // Si aucun argument, afficher l'aide
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // Traiter les sous-commandes
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                return handleReload(sender);

            case "version":
            case "ver":
                return handleVersion(sender);

            case "diagnostic":
            case "diag":
            case "debug":
                // FIX BASSE #30: Commande de diagnostic
                return new com.gravityyfh.roleplaycity.command.DiagnosticCommand(plugin).onCommand(sender, command, label, args);

            case "config":
            case "cfg":
                // FIX BASSE #27: Commande config debug
                return handleConfig(sender);

            case "shop":
                // Sous-commandes shop
                if (args.length >= 2) {
                    if (args[1].equalsIgnoreCase("clean")) {
                        return handleShopClean(sender);
                    } else if (args[1].equalsIgnoreCase("cleanall")) {
                        return handleShopCleanAll(sender);
                    } else {
                        sender.sendMessage(ChatColor.RED + "Usage: /roleplaycity shop <clean|cleanall>");
                        return true;
                    }
                } else {
                    sender.sendMessage(ChatColor.RED + "Usage: /roleplaycity shop <clean|cleanall>");
                    return true;
                }

            case "help":
            case "?":
                sendHelp(sender);
                return true;

            default:
                sender.sendMessage(ChatColor.RED + "Sous-commande inconnue: " + args[0]);
                sender.sendMessage(ChatColor.YELLOW + "Utilisez /roleplaycity help pour voir les commandes disponibles");
                return true;
        }
    }

    /**
     * Gère la commande /roleplaycity reload
     */
    private boolean handleReload(CommandSender sender) {
        // Vérifier la permission
        if (!sender.hasPermission("roleplaycity.admin.reload")) {
            sender.sendMessage(ChatColor.RED + "❌ Vous n'avez pas la permission d'utiliser cette commande.");
            sender.sendMessage(ChatColor.GRAY + "Permission requise: roleplaycity.admin.reload");
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "🔄 RECHARGEMENT DE ROLEPLAYCITY");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        long startTime = System.currentTimeMillis();

        try {
            // Recharger le plugin
            plugin.reloadPluginConfig();

            long duration = System.currentTimeMillis() - startTime;

            sender.sendMessage(ChatColor.AQUA + "💾 Sauvegarde effectuée:");
            sender.sendMessage(ChatColor.GREEN + "  ✓ Villes, Entreprises, Boutiques");
            sender.sendMessage(ChatColor.GREEN + "  ✓ Amendes, Notifications");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "🔄 Rechargement effectué:");
            sender.sendMessage(ChatColor.GREEN + "  ✓ Configuration principale");
            sender.sendMessage(ChatColor.GREEN + "  ✓ Villes & Entreprises");
            sender.sendMessage(ChatColor.GREEN + "  ✓ Boutiques & Niveau");
            sender.sendMessage(ChatColor.GREEN + "  ✓ Système médical & Police");
            sender.sendMessage(ChatColor.GREEN + "  ✓ Cambriolages, Identités, Téléphonie");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ RECHARGEMENT TERMINÉ (données sauvées)");
            sender.sendMessage(ChatColor.GRAY + "Durée: " + ChatColor.WHITE + duration + "ms");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage("");

            // Log pour la console
            plugin.getLogger().info("Plugin rechargé par " + sender.getName() + " en " + duration + "ms");

        } catch (Exception e) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "❌ ERREUR lors du rechargement:");
            sender.sendMessage(ChatColor.RED + e.getMessage());
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "⚠ Consultez la console pour plus de détails");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage("");

            // FIX BASSE: Utiliser logging approprié au lieu de printStackTrace
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Erreur lors du rechargement du plugin", e);
        }

        return true;
    }

    /**
     * Gère la commande /roleplaycity version
     */
    private boolean handleVersion(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        String bukkitVersion = plugin.getServer().getBukkitVersion();

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "📦 ROLEPLAYCITY");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Version: " + ChatColor.WHITE + "v" + version);
        sender.sendMessage(ChatColor.GRAY + "Serveur: " + ChatColor.WHITE + bukkitVersion);
        sender.sendMessage(ChatColor.GRAY + "Auteur: " + ChatColor.WHITE + "GravityyFH");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.AQUA + "Systèmes actifs:");
        sender.sendMessage(ChatColor.GRAY + "  • Villes & Terrains");
        sender.sendMessage(ChatColor.GRAY + "  • Entreprises & Économie");
        sender.sendMessage(ChatColor.GRAY + "  • Police & Justice");
        sender.sendMessage(ChatColor.GRAY + "  • Système Médical RP");
        sender.sendMessage(ChatColor.GRAY + "  • Évolution des Villes");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        return true;
    }

    /**
     * FIX BASSE #27: Affiche les valeurs de configuration principales
     */
    private boolean handleConfig(CommandSender sender) {
        // Vérifier la permission
        if (!sender.hasPermission("roleplaycity.admin.config")) {
            sender.sendMessage(ChatColor.RED + "❌ Vous n'avez pas la permission d'utiliser cette commande.");
            sender.sendMessage(ChatColor.GRAY + "Permission requise: roleplaycity.admin.config");
            return true;
        }

        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfig();

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "⚙ CONFIGURATION ROLEPLAYCITY");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        // === FINANCES ===
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "💰 FINANCES");
        sender.sendMessage(ChatColor.GRAY + "  Taxes: " + ChatColor.WHITE +
            config.getDouble("finance.pourcentage-taxes", 15.0) + "%");
        sender.sendMessage(ChatColor.GRAY + "  Allocation chômage/h: " + ChatColor.WHITE +
            String.format("%.2f€", config.getDouble("finance.allocation-chomage-horaire", 0)));
        sender.sendMessage(ChatColor.GRAY + "  Charge salariale/employé/h: " + ChatColor.WHITE +
            String.format("%.2f€", config.getDouble("finance.charge-salariale-par-employe-horaire", 0)));
        sender.sendMessage(ChatColor.GRAY + "  Max entreprises/gérant: " + ChatColor.WHITE +
            config.getInt("finance.max-entreprises-par-gerant", 1));
        sender.sendMessage(ChatColor.GRAY + "  Max emplois/joueur: " + ChatColor.WHITE +
            config.getInt("finance.max-travail-joueur", 1));
        sender.sendMessage("");

        // === VILLES ===
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "🏛 VILLES");
        sender.sendMessage(ChatColor.GRAY + "  Coût création: " + ChatColor.WHITE +
            String.format("%.2f€", config.getDouble("town.creation-cost", 10000)));
        sender.sendMessage(ChatColor.GRAY + "  Coût rejoindre: " + ChatColor.WHITE +
            String.format("%.2f€", config.getDouble("town.join-cost", 100)));
        sender.sendMessage(ChatColor.GRAY + "  Coût claim/chunk: " + ChatColor.WHITE +
            String.format("%.2f€", config.getDouble("town.claim-cost-per-chunk", 500)));
        sender.sendMessage(ChatColor.GRAY + "  Remboursement unclaim: " + ChatColor.WHITE +
            config.getDouble("town.unclaim-refund-percentage", 50) + "%");
        sender.sendMessage("");

        // === SYSTÈME ===
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "⚙ SYSTÈME");
        sender.sendMessage(ChatColor.GRAY + "  Langue: " + ChatColor.WHITE +
            config.getString("system.language", "fr"));
        sender.sendMessage(ChatColor.GRAY + "  Mode debug: " + (config.getBoolean("system.debug-mode", false) ?
            ChatColor.GREEN + "✓ Activé" : ChatColor.GRAY + "✗ Désactivé"));
        sender.sendMessage(ChatColor.GRAY + "  Longueur SIRET: " + ChatColor.WHITE +
            config.getInt("siret.longueur", 14) + " caractères");
        sender.sendMessage(ChatColor.GRAY + "  Distance invitation: " + ChatColor.WHITE +
            config.getDouble("invitation.distance-max", 10) + " blocs");
        sender.sendMessage("");

        // === NIVEAUX DE VILLE ===
        sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "📊 NIVEAUX DE VILLE");
        String[] levels = {"campement", "village", "ville"};
        for (String level : levels) {
            String path = "town.levels." + level;
            int minPop = config.getInt(path + ".min-population", 0);
            int maxPop = config.getInt(path + ".max-population", 0);
            int maxClaims = config.getInt(path + ".max-claims", 0);

            sender.sendMessage(ChatColor.GRAY + "  " + level.substring(0, 1).toUpperCase() + level.substring(1) + ": " +
                ChatColor.WHITE + minPop + "-" + (maxPop == 999999 ? "∞" : maxPop) + " habitants, " +
                maxClaims + " claims max");
        }
        sender.sendMessage("");

        // === TYPES D'ENTREPRISE ===
        org.bukkit.configuration.ConfigurationSection typesSection = config.getConfigurationSection("types-entreprise");
        if (typesSection != null) {
            int count = typesSection.getKeys(false).size();
            sender.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "🏢 ENTREPRISES");
            sender.sendMessage(ChatColor.GRAY + "  Types disponibles: " + ChatColor.WHITE + count);
            sender.sendMessage(ChatColor.GRAY + "    " + String.join(", ", typesSection.getKeys(false)));
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage(ChatColor.GRAY + "💡 Utilisez " + ChatColor.WHITE + "/roleplaycity diagnostic" +
            ChatColor.GRAY + " pour un rapport système complet");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        return true;
    }

    /**
     * Gère la commande /roleplaycity shop cleanall
     * SUPPRIME TOUS LES ITEMDISPLAY ET ARMORSTAND PROCHES DES SHOPS (même ceux sans métadonnées)
     * Utilisé pour nettoyer les entités orphelines des anciennes versions
     */
    private boolean handleShopCleanAll(CommandSender sender) {
        // Vérifier la permission
        if (!sender.hasPermission("roleplaycity.admin.shop.clean")) {
            sender.sendMessage(ChatColor.RED + "❌ Vous n'avez pas la permission d'utiliser cette commande.");
            sender.sendMessage(ChatColor.GRAY + "Permission requise: roleplaycity.admin.shop.clean");
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "🧹 NETTOYAGE BRUTAL DES HOLOGRAMMES");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.RED + "⚠ ATTENTION: Supprime TOUS les ArmorStands/ItemDisplay");
        sender.sendMessage(ChatColor.RED + "   proches des coffres de shops (même sans métadonnées)");
        sender.sendMessage("");

        long startTime = System.currentTimeMillis();

        try {
            // TODO: Méthode de nettoyage brutal non encore implémentée
            // plugin.getShopManager().cleanupAllShopEntitiesNearChests(sender);
            sender.sendMessage(ChatColor.RED + "Cette fonctionnalité n'est pas encore implémentée.");

            long duration = System.currentTimeMillis() - startTime;

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ NETTOYAGE BRUTAL TERMINÉ");
            sender.sendMessage(ChatColor.GRAY + "Durée: " + ChatColor.WHITE + duration + "ms");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "💡 Utilisez /roleplaycity reload pour recréer les hologrammes");
            sender.sendMessage("");

            // Log pour la console
            plugin.getLogger().info("Nettoyage brutal des hologrammes de shops effectué par " + sender.getName() + " en " + duration + "ms");

        } catch (Exception e) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "❌ ERREUR lors du nettoyage:");
            sender.sendMessage(ChatColor.RED + e.getMessage());
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "⚠ Consultez la console pour plus de détails");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage("");

            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Erreur lors du nettoyage brutal des hologrammes de shops", e);
        }

        return true;
    }

    /**
     * Gère la commande /roleplaycity shop clean
     * Nettoie manuellement tous les hologrammes orphelins (ArmorStands + ItemDisplay)
     */
    private boolean handleShopClean(CommandSender sender) {
        // Vérifier la permission
        if (!sender.hasPermission("roleplaycity.admin.shop.clean")) {
            sender.sendMessage(ChatColor.RED + "❌ Vous n'avez pas la permission d'utiliser cette commande.");
            sender.sendMessage(ChatColor.GRAY + "Permission requise: roleplaycity.admin.shop.clean");
            return true;
        }

        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "🧹 NETTOYAGE DES HOLOGRAMMES DE SHOPS");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GRAY + "Recherche d'hologrammes orphelins...");
        sender.sendMessage("");

        long startTime = System.currentTimeMillis();

        try {
            // TODO: Méthode de nettoyage global non encore implémentée
            // plugin.getShopManager().cleanupAllOrphanedHolograms(sender);
            sender.sendMessage(ChatColor.RED + "Cette fonctionnalité n'est pas encore implémentée.");

            long duration = System.currentTimeMillis() - startTime;

            sender.sendMessage("");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "✓ NETTOYAGE TERMINÉ");
            sender.sendMessage(ChatColor.GRAY + "Durée: " + ChatColor.WHITE + duration + "ms");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage("");

            // Log pour la console
            plugin.getLogger().info("Nettoyage des hologrammes de shops effectué par " + sender.getName() + " en " + duration + "ms");

        } catch (Exception e) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "❌ ERREUR lors du nettoyage:");
            sender.sendMessage(ChatColor.RED + e.getMessage());
            sender.sendMessage("");
            sender.sendMessage(ChatColor.YELLOW + "⚠ Consultez la console pour plus de détails");
            sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
            sender.sendMessage("");

            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Erreur lors du nettoyage des hologrammes de shops", e);
        }

        return true;
    }

    /**
     * Affiche l'aide des commandes
     */
    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage(ChatColor.YELLOW + "" + ChatColor.BOLD + "📖 COMMANDES ROLEPLAYCITY");
        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");

        if (sender.hasPermission("roleplaycity.admin.reload")) {
            sender.sendMessage(ChatColor.AQUA + "/roleplaycity reload");
            sender.sendMessage(ChatColor.GRAY + "  Recharge la configuration du plugin");
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.AQUA + "/roleplaycity version");
        sender.sendMessage(ChatColor.GRAY + "  Affiche la version du plugin");
        sender.sendMessage("");

        // FIX BASSE #27: Ajout commande config
        if (sender.hasPermission("roleplaycity.admin.config")) {
            sender.sendMessage(ChatColor.AQUA + "/roleplaycity config");
            sender.sendMessage(ChatColor.GRAY + "  Affiche les valeurs de configuration");
            sender.sendMessage("");
        }

        // FIX BASSE #30: Ajout commande diagnostic
        if (sender.hasPermission("roleplaycity.admin.diagnostic")) {
            sender.sendMessage(ChatColor.AQUA + "/roleplaycity diagnostic");
            sender.sendMessage(ChatColor.GRAY + "  Affiche un rapport système détaillé");
            sender.sendMessage("");
        }

        // Commandes shop
        if (sender.hasPermission("roleplaycity.admin.shop.clean")) {
            sender.sendMessage(ChatColor.AQUA + "/roleplaycity shop clean");
            sender.sendMessage(ChatColor.GRAY + "  Nettoie les hologrammes de shops orphelins");
            sender.sendMessage("");
            sender.sendMessage(ChatColor.AQUA + "/roleplaycity shop cleanall");
            sender.sendMessage(ChatColor.GRAY + "  Supprime TOUTES les entités proches des shops");
            sender.sendMessage(ChatColor.RED + "  (⚠ Supprime même les entités valides!)");
            sender.sendMessage("");
        }

        sender.sendMessage(ChatColor.AQUA + "/roleplaycity help");
        sender.sendMessage(ChatColor.GRAY + "  Affiche cette aide");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.YELLOW + "Autres commandes:");
        sender.sendMessage(ChatColor.GRAY + "  • /ville - Système de villes");
        sender.sendMessage(ChatColor.GRAY + "  • /entreprise - Système d'entreprises");
        sender.sendMessage(ChatColor.GRAY + "  • /medical - Système médical");
        sender.sendMessage("");

        sender.sendMessage(ChatColor.GOLD + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        sender.sendMessage("");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Proposer les sous-commandes
            if (sender.hasPermission("roleplaycity.admin.reload")) {
                completions.add("reload");
            }
            // FIX BASSE #27: Tab completion pour config
            if (sender.hasPermission("roleplaycity.admin.config")) {
                completions.add("config");
            }
            // FIX BASSE #30: Tab completion pour diagnostic
            if (sender.hasPermission("roleplaycity.admin.diagnostic")) {
                completions.add("diagnostic");
            }
            // Tab completion pour shop
            if (sender.hasPermission("roleplaycity.admin.shop.clean")) {
                completions.add("shop");
            }
            completions.add("version");
            completions.add("help");

            // Filtrer selon ce que l'utilisateur a tapé
            String input = args[0].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        }
        // Tab completion pour /roleplaycity shop <sous-commande>
        else if (args.length == 2 && args[0].equalsIgnoreCase("shop")) {
            if (sender.hasPermission("roleplaycity.admin.shop.clean")) {
                completions.add("clean");
                completions.add("cleanall");
            }

            // Filtrer selon ce que l'utilisateur a tapé
            String input = args[1].toLowerCase();
            completions.removeIf(s -> !s.toLowerCase().startsWith(input));
        }

        return completions;
    }
}
