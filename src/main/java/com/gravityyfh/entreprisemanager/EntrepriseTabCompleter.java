package com.gravityyfh.entreprisemanager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class EntrepriseTabCompleter implements TabCompleter {

    private EntrepriseManagerLogic entrepriseLogic;

    public EntrepriseTabCompleter(EntrepriseManagerLogic entrepriseLogic) {
        this.entrepriseLogic = entrepriseLogic;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return null;
        }
        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            completions.add("create");
            completions.add("delete");
            completions.add("info");
            completions.add("employee");
            completions.add("list");
            completions.add("admin");
            completions.add("withdraw");
            completions.add("deposit");
            completions.add("leave");
            completions.add("kick");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "create":
                    completions.addAll(entrepriseLogic.getPlayersInMayorTown(player));
                    break;
                case "delete":
                case "info":
                    completions.addAll(entrepriseLogic.getGerantsAvecEntreprises());
                    break;
                case "employee":
                    completions.add("invite");
                    break;
                case "list":
                    completions.addAll(entrepriseLogic.getAllTowns());
                    break;
                case "admin":
                    completions.add("forcepay");
                    completions.add("reload");
                    break;
                case "withdraw":
                case "deposit":
                    completions.addAll(entrepriseLogic.getEntrepriseDuGerant(player.getName()).stream()
                            .map(EntrepriseManagerLogic.Entreprise::getNom)
                            .collect(Collectors.toList()));
                    break;
            }
        } else if (args.length == 3) {
            if ("employee".equalsIgnoreCase(args[0]) && "invite".equalsIgnoreCase(args[1])) {
                completions.addAll(entrepriseLogic.getTypesEntrepriseDuGerant(player.getName()));
            } else if ("create".equalsIgnoreCase(args[0])) {
                completions.addAll(entrepriseLogic.getTypesEntreprise());
            } else if ("delete".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0])) {
                String gerant = args[1];
                completions.addAll(entrepriseLogic.getTypesEntrepriseDuGerant(gerant));
            }
        } else if (args.length == 4 && "employee".equalsIgnoreCase(args[0]) && "invite".equalsIgnoreCase(args[1])) {
            completions.addAll(entrepriseLogic.getAllPlayers());
        }

        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
                .collect(Collectors.toList());
    }
}
