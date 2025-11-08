package com.gravityyfh.roleplaycity.medical.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.medical.manager.MedicalSystemManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DieCommand implements CommandExecutor {
    private final MedicalSystemManager medicalManager;

    public DieCommand(RoleplayCity plugin) {
        this.medicalManager = plugin.getMedicalSystemManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est réservée aux joueurs.");
            return true;
        }

        if (!medicalManager.isInjured(player)) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas blessé.");
            return true;
        }

        medicalManager.playerChooseDeath(player);
        return true;
    }
}
