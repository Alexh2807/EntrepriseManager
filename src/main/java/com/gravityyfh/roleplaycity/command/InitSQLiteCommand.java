package com.gravityyfh.roleplaycity.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;

/**
 * Commande pour initialiser SQLite sans migration (base vide).
 */
public class InitSQLiteCommand implements CommandExecutor {

    private final RoleplayCity plugin;

    public InitSQLiteCommand(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Cette commande doit être exécutée par un joueur.");
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("entreprisemanager.admin.init")) {
            player.sendMessage(ChatColor.RED + "Permission refusée.");
            return true;
        }

        // Vérifier si SQLite existe déjà
        File sqliteDb = new File(plugin.getDataFolder(), "entreprises.db");
        if (sqliteDb.exists()) {
            player.sendMessage(ChatColor.RED + "❌ La base SQLite existe déjà !");
            player.sendMessage(ChatColor.YELLOW + "Fichier: " + sqliteDb.getAbsolutePath());
            player.sendMessage(ChatColor.YELLOW + "Si vous voulez réinitialiser, supprimez-le manuellement et relancez la commande.");
            return true;
        }

        // Confirmation requise
        if (args.length < 1 || !args[0].equalsIgnoreCase("confirm")) {
            player.sendMessage(ChatColor.GOLD + "========================================");
            player.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "✓ INITIALISATION SQLite");
            player.sendMessage(ChatColor.GOLD + "========================================");
            player.sendMessage("");
            player.sendMessage(ChatColor.WHITE + "Cette commande va :");
            player.sendMessage(ChatColor.YELLOW + "  1. Créer une base SQLite vide");
            player.sendMessage(ChatColor.YELLOW + "  2. Initialiser les tables");
            player.sendMessage(ChatColor.YELLOW + "  3. Activer le mode SQLite");
            player.sendMessage("");
            player.sendMessage(ChatColor.RED + "⚠ Utilisez cette commande UNIQUEMENT si :");
            player.sendMessage(ChatColor.RED + "  - Vous n'avez pas de données YAML à migrer");
            player.sendMessage(ChatColor.RED + "  - Vous démarrez un nouveau serveur");
            player.sendMessage("");
            player.sendMessage(ChatColor.GREEN + "Pour confirmer :");
            player.sendMessage(ChatColor.AQUA + "  /entreprise admin init confirm");
            player.sendMessage(ChatColor.GOLD + "========================================");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "⏳ Initialisation de SQLite...");

        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Créer le connection manager et initialiser les tables
                com.gravityyfh.roleplaycity.entreprise.persistence.SQLiteConnectionManager connManager =
                    new com.gravityyfh.roleplaycity.entreprise.persistence.SQLiteConnectionManager(plugin);

                connManager.initialize();

                // Fermer la connexion
                connManager.close();

                // Retour au thread principal pour envoyer les messages
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.GOLD + "========================================");
                    player.sendMessage(ChatColor.GREEN + ChatColor.BOLD.toString() + "✓ INITIALISATION RÉUSSIE");
                    player.sendMessage(ChatColor.GOLD + "========================================");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GREEN + "✓ Base SQLite créée: " + sqliteDb.getName());
                    player.sendMessage(ChatColor.GREEN + "✓ Tables initialisées (vides)");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.YELLOW + "📋 Prochaines étapes :");
                    player.sendMessage(ChatColor.WHITE + "  1. Redémarrez le serveur");
                    player.sendMessage(ChatColor.WHITE + "  2. Le mode SQLite sera automatiquement activé");
                    player.sendMessage(ChatColor.WHITE + "  3. Vous pouvez créer vos premières entreprises");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.AQUA + "💡 Le système de cache et auto-save sera actif");
                    player.sendMessage(ChatColor.GOLD + "========================================");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Erreur initialisation SQLite: " + e.getMessage());
                e.printStackTrace();

                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(ChatColor.RED + "❌ ÉCHEC DE L'INITIALISATION");
                    player.sendMessage(ChatColor.RED + "Erreur: " + e.getMessage());
                    player.sendMessage(ChatColor.YELLOW + "Consultez les logs du serveur pour plus de détails");
                });
            }
        });

        return true;
    }
}
