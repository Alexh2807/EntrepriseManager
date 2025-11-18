package com.gravityyfh.roleplaycity.postal.command;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.postal.gui.LaPosteGUI;
import com.gravityyfh.roleplaycity.town.data.MunicipalSubType;
import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.manager.ClaimManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Commande /laposte pour envoyer du courrier
 * Disponible uniquement dans les zones LA_POSTE
 */
public class LaPosteCommand implements CommandExecutor {
    private final RoleplayCity plugin;
    private final ClaimManager claimManager;
    private final LaPosteGUI laPosteGUI;

    public LaPosteCommand(RoleplayCity plugin, ClaimManager claimManager, LaPosteGUI laPosteGUI) {
        this.plugin = plugin;
        this.claimManager = claimManager;
        this.laPosteGUI = laPosteGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Cette commande est réservée aux joueurs.");
            return true;
        }

        // Vérifier que le joueur est dans une zone LA_POSTE
        Plot plot = claimManager.getPlotAt(player.getLocation().getChunk());

        if (plot == null || plot.getMunicipalSubType() != MunicipalSubType.LA_POSTE) {
            player.sendMessage(ChatColor.RED + "✗ Vous devez être dans un bureau de poste pour utiliser cette commande.");
            player.sendMessage(ChatColor.YELLOW + "Rendez-vous à La Poste de votre ville !");
            return true;
        }

        // Ouvrir le GUI principal
        laPosteGUI.openMainMenu(player, plot.getTownName());

        return true;
    }
}
