package com.gravityyfh.roleplaycity.backpack.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackItemManager;
import com.gravityyfh.roleplaycity.backpack.manager.BackpackManager;
import com.gravityyfh.roleplaycity.backpack.util.BackpackUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Gestionnaire de commandes pour les backpacks
 */
public class BackpackCommandHandler implements CommandExecutor, TabCompleter {
    private final RoleplayCity plugin;
    private final BackpackItemManager itemManager;
    private final BackpackManager backpackManager;
    private final BackpackUtil backpackUtil;

    public BackpackCommandHandler(RoleplayCity plugin, BackpackItemManager itemManager,
                                  BackpackManager backpackManager, BackpackUtil backpackUtil) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.backpackManager = backpackManager;
        this.backpackUtil = backpackUtil;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // /backpack give <joueur> <type> [quantité]
        // /backpack reload
        // /backpack info
        // /backpack list

        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give":
                return handleGiveCommand(sender, args);
            case "reload":
                return handleReloadCommand(sender);
            case "info":
                return handleInfoCommand(sender);
            case "list":
                return handleListCommand(sender);
            case "help":
                sendHelpMessage(sender);
                return true;
            default:
                sender.sendMessage(ChatColor.RED + "Sous-commande inconnue: " + subCommand);
                sendHelpMessage(sender);
                return true;
        }
    }

    /**
     * Gère la commande /backpack give
     */
    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("roleplaycity.backpack.give")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /backpack give <joueur> <type> [quantité]");
            sender.sendMessage(ChatColor.YELLOW + "Types disponibles: " + String.join(", ", itemManager.getTypeIds()));
            return true;
        }

        String targetName = args[1];
        String typeId = args[2];
        int amount = 1;

        // Parser la quantité si fournie
        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 64) {
                    sender.sendMessage(ChatColor.RED + "La quantité doit être entre 1 et 64!");
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Quantité invalide: " + args[3]);
                return true;
            }
        }

        // Vérifier que le type existe
        if (itemManager.getType(typeId) == null) {
            sender.sendMessage(ChatColor.RED + "Type de backpack inconnu: " + typeId);
            sender.sendMessage(ChatColor.YELLOW + "Types disponibles: " + String.join(", ", itemManager.getTypeIds()));
            return true;
        }

        // Trouver le joueur cible
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Joueur introuvable: " + targetName);
            return true;
        }

        // Créer et donner les backpacks
        for (int i = 0; i < amount; i++) {
            ItemStack backpack = itemManager.createBackpack(typeId);

            if (backpack == null) {
                sender.sendMessage(ChatColor.RED + "Erreur lors de la création du backpack!");
                return true;
            }

            // Vérifier si l'inventaire est plein
            if (target.getInventory().firstEmpty() == -1) {
                // Inventaire plein, drop au sol
                target.getWorld().dropItemNaturally(target.getLocation(), backpack);
            } else {
                target.getInventory().addItem(backpack);
            }
        }

        // Messages de confirmation
        String typeName = itemManager.getType(typeId).getDisplayName();
        String amountText = amount > 1 ? amount + " backpacks" : "un backpack";
        target.sendMessage(ChatColor.GREEN + "Vous avez reçu " + amountText + " " + ChatColor.translateAlternateColorCodes('&', typeName) + ChatColor.GREEN + "!");
        sender.sendMessage(ChatColor.GREEN + amountText + " (" + typeId + ") donné(s) à " + target.getName() + "!");

        return true;
    }

    /**
     * Gère la commande /backpack reload
     */
    private boolean handleReloadCommand(CommandSender sender) {
        if (!sender.hasPermission("roleplaycity.backpack.reload")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        // Recharger la configuration
        plugin.reloadConfig();
        itemManager.loadConfiguration();
        backpackUtil.loadConfiguration();

        // Vider le cache
        backpackManager.clearCache();

        sender.sendMessage(ChatColor.GREEN + "Configuration des backpacks rechargée avec succès!");
        return true;
    }

    /**
     * Gère la commande /backpack info
     */
    private boolean handleInfoCommand(CommandSender sender) {
        if (!sender.hasPermission("roleplaycity.backpack.info")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande ne peut être utilisée que par un joueur!");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (!itemManager.isBackpack(item)) {
            player.sendMessage(ChatColor.RED + "Vous devez tenir un backpack dans votre main!");
            return true;
        }

        // Récupérer le type
        var type = itemManager.getBackpackType(item);
        if (type == null) {
            player.sendMessage(ChatColor.RED + "Type de backpack inconnu!");
            return true;
        }

        // Afficher les informations
        player.sendMessage(ChatColor.GOLD + "===== Informations du Backpack =====");
        player.sendMessage(ChatColor.YELLOW + "Type: " + ChatColor.WHITE + type.getId() + " " + ChatColor.translateAlternateColorCodes('&', type.getDisplayName()));
        player.sendMessage(ChatColor.YELLOW + "UUID: " + ChatColor.WHITE + itemManager.getBackpackUUID(item));
        player.sendMessage(ChatColor.YELLOW + "Capacité: " + ChatColor.WHITE + type.getSize() + " slots (" + (type.getSize() / 9) + " lignes)");

        // Afficher la whitelist si présente
        if (type.hasWhitelist()) {
            player.sendMessage(ChatColor.YELLOW + "Restrictions: " + ChatColor.WHITE + "Accepte seulement certains items");
        }

        // Charger les données pour afficher le contenu
        var data = backpackManager.loadBackpack(item);
        if (data != null) {
            player.sendMessage(ChatColor.YELLOW + "Items: " + ChatColor.WHITE + data.getItemCount());
            player.sendMessage(ChatColor.YELLOW + "Slots utilisés: " + ChatColor.WHITE + data.getUsedSlots() + "/" + data.size());
            player.sendMessage(ChatColor.YELLOW + "Vide: " + ChatColor.WHITE + (data.isEmpty() ? "Oui" : "Non"));
        }

        player.sendMessage(ChatColor.GOLD + "===================================");
        return true;
    }

    /**
     * Gère la commande /backpack list
     */
    private boolean handleListCommand(CommandSender sender) {
        if (!sender.hasPermission("roleplaycity.backpack.info")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande!");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "===== Types de Backpacks =====");

        for (var type : itemManager.getBackpackTypes().values()) {
            String name = ChatColor.translateAlternateColorCodes('&', type.getDisplayName());
            int lines = type.getSize() / 9;
            String restrictions = type.hasWhitelist() ? ChatColor.YELLOW + " [Restreint]" : "";

            sender.sendMessage(ChatColor.WHITE + "• " + ChatColor.GRAY + type.getId() + ChatColor.DARK_GRAY + " - " + name + ChatColor.GRAY + " (" + lines + " lignes)" + restrictions);
        }

        sender.sendMessage(ChatColor.GOLD + "==============================");
        sender.sendMessage(ChatColor.GRAY + "Total: " + itemManager.getBackpackTypes().size() + " types");

        return true;
    }

    /**
     * Affiche le message d'aide
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "===== Commandes Backpack =====");

        if (sender.hasPermission("roleplaycity.backpack.give")) {
            sender.sendMessage(ChatColor.YELLOW + "/backpack give <joueur> <type> [quantité]" + ChatColor.GRAY + " - Donner un backpack");
        }

        if (sender.hasPermission("roleplaycity.backpack.reload")) {
            sender.sendMessage(ChatColor.YELLOW + "/backpack reload" + ChatColor.GRAY + " - Recharger la configuration");
        }

        if (sender.hasPermission("roleplaycity.backpack.info")) {
            sender.sendMessage(ChatColor.YELLOW + "/backpack info" + ChatColor.GRAY + " - Informations sur un backpack en main");
            sender.sendMessage(ChatColor.YELLOW + "/backpack list" + ChatColor.GRAY + " - Liste des types disponibles");
        }

        sender.sendMessage(ChatColor.GOLD + "==============================");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Sous-commandes
            List<String> subCommands = new ArrayList<>();

            if (sender.hasPermission("roleplaycity.backpack.give")) {
                subCommands.add("give");
            }
            if (sender.hasPermission("roleplaycity.backpack.reload")) {
                subCommands.add("reload");
            }
            if (sender.hasPermission("roleplaycity.backpack.info")) {
                subCommands.add("info");
                subCommands.add("list");
            }
            subCommands.add("help");

            String input = args[0].toLowerCase();
            for (String subCmd : subCommands) {
                if (subCmd.startsWith(input)) {
                    completions.add(subCmd);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            // Noms des joueurs en ligne
            String input = args[1].toLowerCase();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase().startsWith(input)) {
                    completions.add(player.getName());
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            // Types de backpacks
            String input = args[2].toLowerCase();
            for (String typeId : itemManager.getTypeIds()) {
                if (typeId.toLowerCase().startsWith(input)) {
                    completions.add(typeId);
                }
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // Suggestions de quantités
            completions.addAll(Arrays.asList("1", "2", "4", "8", "16", "32", "64"));
        }

        return completions;
    }
}
