package com.gravityyfh.roleplaycity.lotto;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class LottoAdminCommand implements CommandExecutor {

    private final LottoManager lottoManager;

    public LottoAdminCommand(LottoManager lottoManager) {
        this.lottoManager = lottoManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("lotto.admin")) {
            sender.sendMessage(ChatColor.RED + "Vous n'avez pas la permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /lotoadmin <start|stop|draw|reset|reload>");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "start":
                if (lottoManager.getState() == LottoManager.LottoState.OPEN) {
                    sender.sendMessage(ChatColor.YELLOW + "Le loto est déjà ouvert.");
                } else {
                    lottoManager.forceStart();
                    sender.sendMessage(ChatColor.GREEN + "Loto démarré de force.");
                }
                break;

            case "stop":
                if (lottoManager.getState() != LottoManager.LottoState.OPEN) {
                    sender.sendMessage(ChatColor.YELLOW + "Le loto n'est pas ouvert.");
                } else {
                    lottoManager.forceStop();
                    sender.sendMessage(ChatColor.GREEN + "Ventes arrêtées de force.");
                }
                break;

            case "draw":
                lottoManager.forceDraw();
                sender.sendMessage(ChatColor.GREEN + "Tirage forcé lancé.");
                break;

            case "reset":
                lottoManager.reset();
                sender.sendMessage(ChatColor.GREEN + "Loto réinitialisé (Cagnotte perdue).");
                break;

            case "reload":
                lottoManager.reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Configuration du Loto rechargée.");
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Usage: /lotoadmin <start|stop|draw|reset|reload>");
                break;
        }

        return true;
    }
}
