package com.gravityyfh.roleplaycity.lotto;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LottoCommand implements CommandExecutor {

    private final LottoGUI lottoGUI;

    public LottoCommand(LottoGUI lottoGUI) {
        this.lottoGUI = lottoGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Seuls les joueurs peuvent utiliser cette commande.");
            return true;
        }

        Player player = (Player) sender;
        lottoGUI.open(player);
        return true;
    }
}
