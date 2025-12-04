package com.gravityyfh.roleplaycity.identity.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.gui.MainMenuGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class IdentityCommand implements CommandExecutor {

    private final RoleplayCity plugin;
    private final MainMenuGUI mainMenu;

    public IdentityCommand(RoleplayCity plugin) {
        this.plugin = plugin;
        this.mainMenu = plugin.getMainMenuGUI();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }

        if (mainMenu == null) {
            player.sendMessage("§cSystème de menu non disponible.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("menu")) {
            mainMenu.open(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("identite")) {
            // Raccourci vers le sous-menu identité
            if (mainMenu.getIdentityGUI() != null) {
                mainMenu.getIdentityGUI().open(player);
            } else {
                player.sendMessage("§cSystème d'identité non disponible.");
            }
            return true;
        }

        return false;
    }
}
