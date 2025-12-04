package com.gravityyfh.roleplaycity.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Commandes internes pour gérer les requêtes d'interaction (Accept/Refuse)
 * Ces commandes sont appelées par les boutons cliquables dans le chat
 */
public class InteractionRequestCommand implements CommandExecutor {

    private final RoleplayCity plugin;

    public InteractionRequestCommand(RoleplayCity plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(ChatColor.RED + "Usage invalide.");
            return true;
        }

        UUID requestId;
        try {
            requestId = UUID.fromString(args[0]);
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Requête invalide.");
            return true;
        }

        InteractionRequestManager requestManager = plugin.getInteractionRequestManager();
        if (requestManager == null) {
            player.sendMessage(ChatColor.RED + "Système non disponible.");
            return true;
        }

        String cmdName = command.getName().toLowerCase();

        if (cmdName.equals("rpc_internal_accept")) {
            requestManager.acceptRequest(requestId, player);
        } else if (cmdName.equals("rpc_internal_refuse")) {
            requestManager.refuseRequest(requestId, player);
        }

        return true;
    }
}
