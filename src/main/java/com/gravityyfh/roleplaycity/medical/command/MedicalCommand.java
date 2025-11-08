package com.gravityyfh.roleplaycity.medical.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class MedicalCommand implements CommandExecutor {
    private final MedicalSystemManager medicalManager;

    public MedicalCommand(RoleplayCity plugin) {
        this.medicalManager = plugin.getMedicalSystemManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est réservée aux joueurs.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "accept" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /medical accept <missionId>");
                    return true;
                }
                try {
                    UUID missionId = UUID.fromString(args[1]);
                    medicalManager.acceptMission(player, missionId);
                } catch (IllegalArgumentException e) {
                    player.sendMessage(ChatColor.RED + "ID de mission invalide.");
                }
                return true;
            }
            default -> {
                sendUsage(player);
                return true;
            }
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Commandes Médicales ===");
        player.sendMessage(ChatColor.YELLOW + "/medical accept <id> " + ChatColor.GRAY + "- Accepter une mission médicale");
    }
}
