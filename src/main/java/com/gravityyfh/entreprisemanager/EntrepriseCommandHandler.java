package com.gravityyfh.entreprisemanager;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class EntrepriseCommandHandler implements CommandExecutor {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseGUI entrepriseGUI;

    public EntrepriseCommandHandler(EntrepriseManagerLogic entrepriseLogic, EntrepriseGUI entrepriseGUI) {
        this.entrepriseLogic = entrepriseLogic;
        this.entrepriseGUI = entrepriseGUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Seules les joueurs peuvent exécuter cette commande.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            entrepriseGUI.openMainMenu(player); // Ouvre le menu principal si aucun argument n'est fourni
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                handleCreateCommand(player, args);
                break;
            case "delete":
                handleDeleteCommand(player, args);
                break;
            case "employee":
                handleEmployeeCommand(player, args);
                break;
            case "list":
                handleListCommand(player, args);
                break;
            case "info":
                handleInfoCommand(player, args);
                break;
            case "admin":
                handleAdminCommand(player, args);
                break;
            case "accepter":
                entrepriseLogic.handleAccepterCommand(player);
                break;
            case "refuser":
                entrepriseLogic.handleRefuserCommand(player);
                break;
            case "withdraw":
                handleWithdrawCommand(player);
                break;
            case "selectwithdraw":
                handleSelectWithdrawCommand(player, args);
                break;
            case "deposit":
                handleDepositCommand(player);
                break;
            case "selectdeposit":
                handleSelectDepositCommand(player, args);
                break;
            case "leave":
                handleLeaveCommand(player);
                break;
            case "kick":
                handleKickCommand(player);
                break;
            case "confirmkick":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /entreprise confirmkick <NomEntreprise> <NomEmployé>");
                } else {
                    String nomEntreprise = args[1];
                    String nomEmploye = args[2];
                    entrepriseLogic.kickEmploye(player, nomEntreprise, nomEmploye);
                }
                break;
            case "confirmleave":
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /entreprise confirmleave <NomEntreprise>");
                } else {
                    entrepriseLogic.leaveEntreprise(player, args[1]);
                }
                break;
            case "rename":
                if (args.length < 4) {
                    player.sendMessage(ChatColor.RED + "Usage: /entreprise rename <gerant> <type> <nouveauNom>");
                } else {
                    String gerant = args[1];
                    String type = args[2];
                    String nouveauNom = args[3];
                    entrepriseLogic.renameEntreprise(player, gerant, type, nouveauNom);
                }
                break;
            default:
                player.sendMessage("Commande inconnue.");
                return false;
        }
        return true;
    }

    private void handleRenameCommand(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise rename <gerant> <type> <nouveauNom>");
            return;
        }

        String gerant = args[1];
        String type = args[2];
        String nouveauNom = args[3];

        entrepriseLogic.changerNomEntreprise(player, gerant, type, nouveauNom);
    }


    private final HashMap<UUID, String> invitations = new HashMap<>();

    private void handleCreateCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise create <Gérant> <Type>");
            return;
        }

        String gerant = args[1];
        String type = args[2];

        // Vérifier si le joueur est le maire de sa ville
        if (!entrepriseLogic.estMaire(player)) {
            player.sendMessage(ChatColor.RED + "Vous devez être le maire de votre ville pour créer une entreprise.");
            return;
        }
        // Vérifier si le gérant est membre de la ville du maire
        if (!entrepriseLogic.estMembreDeLaVille(gerant, entrepriseLogic.getTownNameFromPlayer(player))) {
            player.sendMessage(ChatColor.RED + "Le gérant spécifié doit être membre de votre ville.");
            return;
        }
        // Vérifier si le gérant peut avoir une autre entreprise
        Player gerantPlayer = Bukkit.getPlayerExact(gerant);
        if (gerantPlayer == null || !entrepriseLogic.peutCreerEntreprise(gerantPlayer)) {
            player.sendMessage(ChatColor.RED + "Le gérant a déjà atteint le nombre maximum d'entreprises autorisées ou n'est pas en ligne.");
            return;
        }

        // Vérifier si le type d'entreprise est valide
        if (!entrepriseLogic.getTypesEntreprise().contains(type)) {
            player.sendMessage(ChatColor.RED + "Type d'entreprise invalide.");
            return;
        }

        // Créer l'entreprise
        String nomEntreprise = "Entreprise_" + UUID.randomUUID().toString().substring(0, 5);
        String siret = entrepriseLogic.generateSiret();
        entrepriseLogic.declareEntreprise(player, entrepriseLogic.getTownNameFromPlayer(player), nomEntreprise, type, gerant, siret);
    }
    private HashMap<UUID, String> entrepriseSelectionneePourRetrait = new HashMap<>();

    private void handleWithdrawCommand(Player player) {
        List<EntrepriseManagerLogic.Entreprise> entreprisesDuGerant = entrepriseLogic.getEntrepriseDuGerant(player.getName());

        if (entreprisesDuGerant.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas gérant d'une entreprise.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Sélectionnez l'entreprise dont vous souhaitez retirer de l'argent :");
        for (EntrepriseManagerLogic.Entreprise entreprise : entreprisesDuGerant) {
            TextComponent message = new TextComponent(ChatColor.YELLOW + entreprise.getNom() + " - Solde: " + entreprise.getSolde() + "€");
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise selectwithdraw " + entreprise.getNom()));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour choisir cette entreprise").create()));
            player.spigot().sendMessage(message);
        }
    }

    private void handleSelectWithdrawCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Veuillez spécifier le nom de l'entreprise.");
            return;
        }
        String entrepriseNom = args[1];
        entrepriseSelectionneePourRetrait.put(player.getUniqueId(), entrepriseNom);

        // Indiquer au système d'écouter le prochain message du joueur
        EntrepriseManager.getInstance().getChatListener().attendreMontantRetrait(player, entrepriseNom);
    }

    private void handleDepositCommand(Player player) {
        List<EntrepriseManagerLogic.Entreprise> entreprisesDuGerant = entrepriseLogic.getEntrepriseDuGerant(player.getName());

        if (entreprisesDuGerant.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes pas gérant d'une entreprise.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Sélectionnez l'entreprise dans laquelle vous souhaitez déposer de l'argent :");
        for (EntrepriseManagerLogic.Entreprise entreprise : entreprisesDuGerant) {
            TextComponent message = new TextComponent(ChatColor.YELLOW + entreprise.getNom() + " - Solde: " + entreprise.getSolde() + "€");
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise selectdeposit " + entreprise.getNom()));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour choisir cette entreprise").create()));
            player.spigot().sendMessage(message);
        }
    }

    private void handleSelectDepositCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Veuillez spécifier le nom de l'entreprise.");
            return;
        }
        String entrepriseNom = args[1];
        entrepriseSelectionneePourRetrait.put(player.getUniqueId(), entrepriseNom);

        // Indiquer au système d'écouter le prochain message du joueur
        EntrepriseManager.getInstance().getChatListener().attendreMontantDepot(player, entrepriseNom);
    }


    private void handleLeaveCommand(Player player) {
        List<Map<String, String>> entreprisesDuJoueurInfo = entrepriseLogic.getEntreprisesDuJoueurInfo(player.getName());

        if (entreprisesDuJoueurInfo.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Vous n'appartenez à aucune entreprise.");
            return;
        }

        player.sendMessage(ChatColor.GOLD + "Cliquez sur l'entreprise que vous souhaitez quitter :");
        for (Map<String, String> entrepriseInfo : entreprisesDuJoueurInfo) {
            TextComponent message = new TextComponent(ChatColor.YELLOW + entrepriseInfo.get("nom"));
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise confirmleave " + entrepriseInfo.get("nom")));
            message.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour quitter " + entrepriseInfo.get("nom")).create()));
            player.spigot().sendMessage(message);
        }
    }
    private void handleKickCommand(Player player) {
        List<EntrepriseManagerLogic.Entreprise> entreprisesDuGerant = entrepriseLogic.getEntrepriseDuGerant(player.getName());

        if (entreprisesDuGerant.isEmpty()) {
            player.sendMessage(ChatColor.RED + "Vous ne gérez aucune entreprise.");
            return;
        }

        for (EntrepriseManagerLogic.Entreprise entreprise : entreprisesDuGerant) {
            Set<String> employes = entreprise.getEmployes();
            if (employes.isEmpty()) {
                continue; // Si l'entreprise n'a pas d'employés, passez à la suivante
            }

            player.sendMessage(ChatColor.GOLD + "handleInfoCommand " + ChatColor.BLUE + entreprise.getNom() + ChatColor.GOLD + " (" + entreprise.getType() + ") :");
            for (String employe : employes) {
                TextComponent message = new TextComponent(ChatColor.YELLOW + employe + " ");
                TextComponent virerButton = new TextComponent(ChatColor.RED + "[Virer]");
                virerButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/entreprise confirmkick " + entreprise.getNom() + " " + employe));
                virerButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("Cliquez pour virer " + employe + " de " + entreprise.getNom()).create()));

                player.spigot().sendMessage(message, virerButton);
            }
        }

        if (entreprisesDuGerant.stream().allMatch(e -> e.getEmployes().isEmpty())) {
            player.sendMessage(ChatColor.RED + "Aucune de vos entreprises n'a d'employés.");
        }
    }




    private void handleDeleteCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise delete <Gérant> <Type>");
            return;
        }

        String gerant = args[1];
        String type = args[2];

        // Trouver l'entreprise
        String nomEntreprise = entrepriseLogic.trouverNomEntrepriseParType(gerant, type);
        if (nomEntreprise == null) {
            player.sendMessage(ChatColor.RED + "Aucune entreprise trouvée pour " + gerant + " avec le type " + type);
            return;
        }

        // Vérifier si le joueur est le maire, le gérant, ou si la ville est supprimée
        if (entrepriseLogic.estMaire(player) || entrepriseLogic.estGerant(player.getName(), nomEntreprise) || entrepriseLogic.estVilleSupprimee(entrepriseLogic.getTownNameFromPlayer(player))) {
            entrepriseLogic.closeEntreprise(player, entrepriseLogic.getTownNameFromPlayer(player), nomEntreprise);
            player.sendMessage(ChatColor.GREEN + "Entreprise " + type + " de " + gerant + " à mis la clé sous la porte.");
        } else {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas l'autorisation de détruire cette entreprise.");
        }
    }



    private void handleEmployeeCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("Usage: /entreprise employee invite <TypeEntrepriseDuGérant> <Joueur>");
            return;
        }

        String action = args[1];
        if (!"invite".equalsIgnoreCase(action)) {
            player.sendMessage(ChatColor.RED + "Action inconnue. Utilisez 'invite'.");
            return;
        }

        if (args.length < 4) {
            player.sendMessage("Usage: /entreprise employee invite <TypeEntrepriseDuGérant> <Joueur>");
            return;
        }

        String typeEntreprise = args[2];
        String joueurNom = args[3];
        Player joueurInvite = Bukkit.getPlayerExact(joueurNom);

        if (joueurInvite == null) {
            player.sendMessage(ChatColor.RED + "Le joueur n'est pas en ligne.");
            return;
        }

        if (joueurNom.equalsIgnoreCase(player.getName())) {
            player.sendMessage(ChatColor.RED + "Vous ne pouvez pas vous inviter vous-même.");
            return;
        }

        // Vérifier si le joueur qui exécute la commande est bien le gérant d'une entreprise du type spécifié
        EntrepriseManagerLogic.Entreprise entrepriseDuGerant = entrepriseLogic.getEntrepriseDuGerantEtType(player.getName(), typeEntreprise);
        if (entrepriseDuGerant == null) {
            player.sendMessage(ChatColor.RED + "Vous n'êtes gérant d'aucune entreprise de type " + typeEntreprise + ".");
            return;
        }

        // Vérifier si l'entreprise peut ajouter un employé
        if (!entrepriseLogic.peutAjouterEmploye(entrepriseDuGerant.getNom())) {
            player.sendMessage(ChatColor.RED + "Votre entreprise a atteint le nombre maximum d'employés.");
            return;
        }

        // Inviter le joueur à l'entreprise
        entrepriseLogic.inviterEmploye(player, entrepriseDuGerant.getNom(), joueurInvite);
    }


    private void handleListCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /entreprise list <ville>");
            return;
        }
        String ville = args[1];
        entrepriseLogic.listEntreprises(player, ville);
    }

    private void handleInfoCommand(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise info <gerant> <type>");
            return;
        }

        String gerant = args[1];
        String type = args[2];
        // Trouver l'entreprise correspondant au gérant et au type
        String nomEntreprise = entrepriseLogic.trouverNomEntrepriseParTypeEtGerant(gerant, type);
        if (nomEntreprise == null) {
            player.sendMessage(ChatColor.RED + "Aucune entreprise trouvée pour " + gerant + " avec le type " + type);
            return;
        }

        // Afficher les informations de l'entreprise
        entrepriseLogic.getEntrepriseInfo(player, nomEntreprise);
    }


    // Méthodes pour la gestion des commandes admin
    private void handleAdminCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("Usage: /entreprise admin <sous-commande> [arguments...]");
            return;
        }

        String subCommand = args[1].toLowerCase();

        switch (subCommand) {
            case "forcepay":
                adminForcePayCommand(player);
                break;
            case "reload":
                adminReloadCommand(player);
                break;
            default:
                player.sendMessage("Sous-commande admin inconnue.");
                break;
        }
    }
    private void adminReloadCommand(Player player) {
        if (!player.hasPermission("entreprisemanager.admin.reload")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de recharger le plugin.");
            return;
        }

        // Vérifiez si votre classe EntrepriseManagerLogic a une méthode pour obtenir l'instance du plugin
        // Par exemple, vous pourriez avoir une méthode getPlugin() dans EntrepriseManagerLogic
        EntrepriseManager pluginInstance = entrepriseLogic.getPlugin();
        if (pluginInstance != null) {
            pluginInstance.reloadPlugin();
            player.sendMessage(ChatColor.GREEN + "Le plugin EntrepriseManager a été rechargé avec succès.");
        } else {
            player.sendMessage(ChatColor.RED + "Impossible de recharger le plugin. L'instance du plugin est introuvable.");
        }
    }
    private void adminForcePayCommand(Player player) {
        if (!player.hasPermission("entreprisemanager.admin.forcepay")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de forcer les paiements.");
            return;
        }

        entrepriseLogic.traiterPaiementsJournaliers();
        player.sendMessage(ChatColor.GREEN + "Les paiements journaliers ont été forcés avec succès.");
    }
}