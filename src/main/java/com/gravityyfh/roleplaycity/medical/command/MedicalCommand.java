package com.gravityyfh.roleplaycity.medical.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager;
import org.bukkit.Bukkit;
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
            // Permettre l'exécution console pour 'reanimer' ? Oui, pourquoi pas.
            // Mais le code actuel structure tout autour de 'player'.
            // Pour faire simple, on garde la restriction joueur pour l'instant, ou on adapte.
            // Vu le check 'instanceof Player' au début, je vais rester sur joueur.
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
            case "reanimer", "revive" -> {
                if (!player.hasPermission("roleplaycity.medical.admin")) {
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /medical reanimer <joueur/all>");
                    return true;
                }

                String targetName = args[1];

                if (targetName.equalsIgnoreCase("all")) {
                    int count = 0;
                    for (Player target : Bukkit.getOnlinePlayers()) {
                        if (medicalManager.isInjured(target)) {
                            medicalManager.revivePlayerOnKick(target);
                            count++;
                        }
                    }
                    player.sendMessage(ChatColor.GREEN + "Succès: " + count + " joueur(s) réanimé(s).");
                } else {
                    Player target = Bukkit.getPlayer(targetName);
                    if (target == null || !target.isOnline()) {
                        player.sendMessage(ChatColor.RED + "Joueur introuvable.");
                        return true;
                    }

                    if (!medicalManager.isInjured(target)) {
                        player.sendMessage(ChatColor.RED + "Ce joueur n'est pas blessé.");
                        return true;
                    }

                    medicalManager.revivePlayerOnKick(target);
                    player.sendMessage(ChatColor.GREEN + "Succès: " + target.getName() + " a été réanimé.");
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
        if (player.hasPermission("roleplaycity.medical.admin")) {
            player.sendMessage(ChatColor.YELLOW + "/medical reanimer <joueur/all> " + ChatColor.GRAY + "- Réanimer un joueur (Admin)");
        }
    }
}
