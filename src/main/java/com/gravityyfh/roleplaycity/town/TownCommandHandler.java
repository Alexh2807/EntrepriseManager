package com.gravityyfh.roleplaycity.town;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.town.gui.TownMainGUI;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TownCommandHandler implements CommandExecutor {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final TownMainGUI townGUI;

    public TownCommandHandler(RoleplayCity plugin, TownManager townManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.townGUI = new TownMainGUI(plugin, townManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seuls les joueurs peuvent exécuter cette commande.");
            return true;
        }

        // Sans arguments : ouvrir le GUI principal
        if (args.length == 0) {
            townGUI.openMainMenu(player);
            return true;
        }

        // Sous-commandes (pour debug ou futures fonctionnalités)
        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> {
                String townName = townManager.getPlayerTown(player.getUniqueId());
                if (townName == null) {
                    player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
                    return true;
                }
                player.sendMessage(ChatColor.GREEN + "Vous êtes dans la ville: " + ChatColor.GOLD + townName);
                return true;
            }
            case "list" -> {
                player.sendMessage(ChatColor.GOLD + "=== Villes du serveur ===");
                for (String town : townManager.getTownNames()) {
                    player.sendMessage(ChatColor.YELLOW + "- " + town);
                }
                return true;
            }
            case "gui" -> {
                townGUI.openMainMenu(player);
                return true;
            }
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /ville accept <nom_ville>");
                    return true;
                }
                String townName = args[1];
                if (townManager.acceptInvitation(player, townName)) {
                    player.sendMessage(ChatColor.GREEN + "Vous avez rejoint la ville " + ChatColor.GOLD + townName + ChatColor.GREEN + "!");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible d'accepter l'invitation.");
                    player.sendMessage(ChatColor.GRAY + "- Vérifiez que vous avez bien été invité");
                    player.sendMessage(ChatColor.GRAY + "- Vérifiez que vous n'êtes pas déjà dans une ville");
                }
                return true;
            }
            case "refuse" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /ville refuse <nom_ville>");
                    return true;
                }
                String townName = args[1];
                if (townManager.refuseInvitation(player, townName)) {
                    player.sendMessage(ChatColor.YELLOW + "Vous avez refusé l'invitation de " + ChatColor.GOLD + townName + ChatColor.YELLOW + ".");
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de refuser l'invitation.");
                }
                return true;
            }
            case "groupes", "groups" -> {
                String townName = townManager.getPlayerTown(player.getUniqueId());
                if (townName == null) {
                    player.sendMessage(ChatColor.RED + "Vous n'êtes dans aucune ville.");
                    return true;
                }
                plugin.getPlotGroupManagementGUI().openMainMenu(player, townName);
                return true;
            }
            case "finishgrouping" -> {
                // Commande interne pour terminer le groupement
                if (plugin.getPlotGroupingListener() != null) {
                    plugin.getPlotGroupingListener().finishGrouping(player);
                }
                return true;
            }
            case "cancelgrouping" -> {
                // Commande interne pour annuler le groupement
                if (plugin.getPlotGroupingListener() != null) {
                    plugin.getPlotGroupingListener().cancelSession(player);
                }
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.RED + "Sous-commande inconnue.");
                townGUI.openMainMenu(player);
                return true;
            }
        }
    }
}
