package com.gravityyfh.roleplaycity.mdt.schematic;

import com.gravityyfh.roleplaycity.mdt.MDTRushManager;
import com.gravityyfh.roleplaycity.RoleplayCity;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commande pour gérer les schématiques FAWE du MDT
 */
public class MDTSchematicCommand implements CommandExecutor, TabCompleter {

    private final RoleplayCity plugin;
    private final MDTRushManager mdtManager;

    public MDTSchematicCommand(RoleplayCity plugin, MDTRushManager mdtManager) {
        this.plugin = plugin;
        this.mdtManager = mdtManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mdt.admin")) {
            sender.sendMessage(ChatColor.RED + "❌ Vous n'avez pas la permission d'utiliser cette commande !");
            return true;
        }

        MDTSchematicManager schematicManager = mdtManager.getSchematicManager();
        MDTRegionManager regionManager = mdtManager.getRegionManager();

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "save":
                handleSave(sender, schematicManager, args);
                break;

            case "restore":
                handleRestore(sender, schematicManager, args);
                break;

            case "list":
                handleList(sender, schematicManager);
                break;

            case "tools":
                handleTools(sender, schematicManager);
                break;

            case "info":
                handleInfo(sender, schematicManager);
                break;

            case "protect":
                handleProtection(sender, regionManager, args);
                break;

            case "bypass":
                handleBypass(sender, regionManager);
                break;

            case "selection":
                handleSelection(sender, schematicManager);
                break;

            default:
                showHelp(sender);
                break;
        }

        return true;
    }

    private void handleSave(CommandSender sender, MDTSchematicManager manager, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ Cette commande doit être exécutée par un joueur !");
            return;
        }

        Player player = (Player) sender;

        if (!manager.hasFAWE()) {
            player.sendMessage(ChatColor.RED + "❌ FAWE n'est pas disponible sur ce serveur !");
            return;
        }

        String name = args.length > 1 ? args[1] : null;

        // Cas 1: "/mdtschematic save" sans argument
        if (name == null) {
            // Vérifier si le joueur a une sélection active
            MDTSchematicManager.RegionSelection selection = manager.getPlayerSelection(player);
            
            if (selection != null) {
                // PRIORITÉ: Si sélection -> On définit l'arène (sauvegarde + update config)
                player.sendMessage(ChatColor.YELLOW + "⏳ Sélection détectée ! Définition de l'arène MDT...");
                manager.saveArenaFromSelection(player);
            } else {
                // SINON: On essaie de sauvegarder la région définie dans la config
                sender.sendMessage(ChatColor.YELLOW + "⏳ Aucune sélection. Sauvegarde de la région MDT (config)...");
                manager.saveMDTRegion().thenAccept(success -> {
                    if (success) {
                        sender.sendMessage(ChatColor.GREEN + "✅ Région MDT sauvegardée avec succès !");
                        sender.sendMessage(ChatColor.GRAY + "Taille: " + formatFileSize(manager.getSchematicFileSize()));
                    } else {
                        sender.sendMessage(ChatColor.RED + "❌ Échec ! Faites une sélection avec la hache en bois (//wand) pour définir l'arène.");
                    }
                });
            }
            return;
        }

        // Cas 2: "/mdtschematic save mdt_arena" ou "arena" -> Force la définition de l'arène
        if (name.equalsIgnoreCase("mdt_arena") || name.equalsIgnoreCase("arena")) {
            MDTSchematicManager.RegionSelection selection = manager.getPlayerSelection(player);
            if (selection == null) {
                player.sendMessage(ChatColor.RED + "❌ Vous devez faire une sélection FAWE pour définir l'arène !");
                return;
            }
            manager.saveArenaFromSelection(player);
            return;
        }

        // Cas 3: "/mdtschematic save <nom>" -> Sauvegarde personnalisée
        manager.saveCustomRegion(player, name).thenAccept(success -> {
            if (success) {
                player.sendMessage(ChatColor.GREEN + "✅ Schématique personnalisée sauvegardée !");
            } else {
                player.sendMessage(ChatColor.RED + "❌ Échec de la sauvegarde !");
            }
        });
    }

    private void handleRestore(CommandSender sender, MDTSchematicManager manager, String[] args) {
        String schematicName = args.length > 1 ? args[1] : "latest";

        if (!manager.hasFAWE()) {
            sender.sendMessage(ChatColor.RED + "❌ FAWE n'est pas disponible sur ce serveur !");
            return;
        }

        sender.sendMessage(ChatColor.YELLOW + "⏳ Restauration de la schématique '" + schematicName + "' en cours...");

        manager.restoreSchematic(schematicName).thenAccept(success -> {
            if (success) {
                sender.sendMessage(ChatColor.GREEN + "✅ Schématique '" + schematicName + "' restaurée avec succès !");
            } else {
                sender.sendMessage(ChatColor.RED + "❌ Échec de la restauration de la schématique '" + schematicName + "' !");
            }
        });
    }

    private void handleList(CommandSender sender, MDTSchematicManager manager) {
        File[] schematics = manager.listSchematics();

        if (schematics == null || schematics.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "📂 Aucune schématique trouvée.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "📂 Schématiques disponibles (" + schematics.length + "):");

        for (File schematic : schematics) {
            String name = schematic.getName().replace(".schem", "").replace(".schematic", "");
            long size = schematic.length();
            String sizeStr = formatFileSize(size);

            sender.sendMessage(ChatColor.GRAY + "  • " + ChatColor.WHITE + name + ChatColor.GRAY + " (" + sizeStr + ")");
        }
    }

    private void handleTools(CommandSender sender, MDTSchematicManager manager) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ Cette commande doit être exécutée par un joueur !");
            return;
        }

        Player player = (Player) sender;
        manager.giveSelectionTools(player);
    }

    private void handleInfo(CommandSender sender, MDTSchematicManager manager) {
        sender.sendMessage(ChatColor.GOLD + "ℹ️ Informations système FAWE:");
        sender.sendMessage(ChatColor.GRAY + "  • FAWE disponible: " +
                          (manager.hasFAWE() ? ChatColor.GREEN + "Oui" : ChatColor.RED + "Non"));
        sender.sendMessage(ChatColor.GRAY + "  • Schématique sauvegardée: " +
                          (manager.hasSavedSchematic() ? ChatColor.GREEN + "Oui" : ChatColor.RED + "Non"));

        if (manager.hasSavedSchematic()) {
            sender.sendMessage(ChatColor.GRAY + "  • Taille du fichier: " + ChatColor.WHITE +
                             formatFileSize(manager.getSchematicFileSize()));
        }
    }

    private void handleProtection(CommandSender sender, MDTRegionManager manager, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /mdtschematic protect <on|off> [raison]");
            return;
        }

        boolean enable = args[1].equalsIgnoreCase("on");
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) :
                       (enable ? "Protection activée manuellement" : "");

        if (enable) {
            manager.enableProtection(reason);
            sender.sendMessage(ChatColor.GREEN + "✅ Protection de la région MDT activée: " + reason);
        } else {
            manager.disableProtection();
            sender.sendMessage(ChatColor.YELLOW + "⚠️ Protection de la région MDT désactivée");
        }
    }

    private void handleBypass(CommandSender sender, MDTRegionManager manager) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ Cette commande doit être exécutée par un joueur !");
            return;
        }

        Player player = (Player) sender;
        manager.addBypassPlayer(player);
    }

    private void handleSelection(CommandSender sender, MDTSchematicManager manager) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "❌ Cette commande doit être exécutée par un joueur !");
            return;
        }

        Player player = (Player) sender;

        if (!manager.hasFAWE()) {
            player.sendMessage(ChatColor.RED + "❌ FAWE n'est pas disponible !");
            return;
        }

        MDTSchematicManager.RegionSelection selection = manager.getPlayerSelection(player);

        if (selection == null) {
            player.sendMessage(ChatColor.YELLOW + "⚠️ Vous n'avez aucune sélection !");
            player.sendMessage(ChatColor.GRAY + "Utilisez /mdtschematic tools pour obtenir les outils de sélection.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "📐 Votre sélection actuelle:");
        player.sendMessage(ChatColor.WHITE + selection.toString());
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== Commandes MDT Schématique (FAWE) ===");
        sender.sendMessage(ChatColor.YELLOW + "/mdtschematic save [nom]" + ChatColor.GRAY + " - Sauvegarde la région MDT ou votre sélection");
        sender.sendMessage(ChatColor.YELLOW + "/mdtschematic restore [nom]" + ChatColor.GRAY + " - Restaure une schématique");
        sender.sendMessage(ChatColor.YELLOW + "/mdtschematic list" + ChatColor.GRAY + " - Liste les schématiques");
        sender.sendMessage(ChatColor.YELLOW + "/mdtschematic tools" + ChatColor.GRAY + " - Donne les outils de sélection FAWE");
        sender.sendMessage(ChatColor.YELLOW + "/mdtschematic selection" + ChatColor.GRAY + " - Affiche votre sélection actuelle");
        sender.sendMessage(ChatColor.YELLOW + "/mdtschematic info" + ChatColor.GRAY + " - Affiche les informations du système");
        sender.sendMessage(ChatColor.YELLOW + "/mdtschematic protect <on|off> [raison]" + ChatColor.GRAY + " - Gère la protection");
        sender.sendMessage(ChatColor.YELLOW + "/mdtschematic bypass" + ChatColor.GRAY + " - Vous autorise à bypasser la protection");
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mdt.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("save", "restore", "list", "tools", "info", "protect", "bypass", "selection");
            return subCommands.stream()
                    .filter(cmd -> cmd.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String subCommand = args[0].toLowerCase();

            if (subCommand.equals("protect")) {
                return Arrays.asList("on", "off");
            }

            if (subCommand.equals("restore")) {
                MDTSchematicManager manager = mdtManager.getSchematicManager();
                File[] schematics = manager.listSchematics();
                if (schematics != null) {
                    return Arrays.stream(schematics)
                            .map(file -> file.getName().replace(".schem", "").replace(".schematic", ""))
                            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        return new ArrayList<>();
    }
}