package com.gravityyfh.roleplaycity.mairie.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.mairie.gui.MairieGUI;
import com.gravityyfh.roleplaycity.mairie.service.AppointmentManager;
import com.gravityyfh.roleplaycity.town.data.Town;
import com.gravityyfh.roleplaycity.town.manager.TownManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Commande /mairie - Accès aux services municipaux
 * Usage: /mairie [ville]
 */
public class MairieCommand implements CommandExecutor, TabCompleter {

    private final RoleplayCity plugin;
    private final TownManager townManager;
    private final AppointmentManager appointmentManager;

    public MairieCommand(RoleplayCity plugin, TownManager townManager, AppointmentManager appointmentManager) {
        this.plugin = plugin;
        this.townManager = townManager;
        this.appointmentManager = appointmentManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est reservee aux joueurs.");
            return true;
        }

        String townName = null;

        // Si un argument est fourni, utiliser cette ville
        if (args.length > 0) {
            townName = args[0];
            Town town = townManager.getTown(townName);
            if (town == null) {
                player.sendMessage(ChatColor.RED + "Ville introuvable: " + townName);
                player.sendMessage(ChatColor.GRAY + "Utilisez /mairie pour voir les villes disponibles.");
                return true;
            }
            townName = town.getName(); // Normaliser le nom
        } else {
            // Sinon, détecter la ville du joueur (citoyenneté)
            String playerTownName = townManager.getPlayerTown(player.getUniqueId());
            if (playerTownName != null) {
                townName = playerTownName;
            }
        }

        // Si aucune ville trouvée, afficher les villes disponibles
        if (townName == null) {
            player.sendMessage(ChatColor.GOLD + "=== Services Mairie ===");
            player.sendMessage(ChatColor.GRAY + "Specifiez une ville:");
            player.sendMessage(ChatColor.YELLOW + "/mairie <ville>");
            player.sendMessage("");
            player.sendMessage(ChatColor.GRAY + "Villes disponibles:");
            for (Town t : townManager.getTowns().values()) {
                player.sendMessage(ChatColor.WHITE + " - " + t.getName());
            }
            return true;
        }

        // Ouvrir le GUI de la mairie
        openMairieGUI(player, townName);
        return true;
    }

    private void openMairieGUI(Player player, String townName) {
        MairieGUI mairieGUI = new MairieGUI(
                plugin,
                townManager,
                plugin.getIdentityManager(),
                appointmentManager,
                townName
        );
        mairieGUI.open(player);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            return townManager.getTowns().values().stream()
                    .map(Town::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
