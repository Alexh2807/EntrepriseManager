package com.gravityyfh.entreprisemanager;

// ... (imports existants)
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import java.util.*;
import java.util.stream.Collectors;

import static com.gravityyfh.entreprisemanager.EntrepriseManagerLogic.plugin;


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
            sender.sendMessage("Seuls les joueurs peuvent exécuter cette commande.");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            entrepriseGUI.openMainMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            // Commandes principales souvent gérées par GUI mais avec fallback commande direct
            case "create": // Proposer une création d'entreprise (Maire uniquement)
                handleCreateCommand(player, args);
                break;
            case "delete": // Dissoudre sa propre entreprise (Gérant) ou via admin
                handleDeleteCommand(player, args);
                break;
            case "info": // Voir les infos d'une entreprise
                handleInfoCommand(player, args);
                break;
            case "list": // Lister les entreprises d'une ville
                handleListCommand(player, args);
                break;
            case "rename": // Renommer son entreprise (Gérant)
                handleRenameCommandDirect(player, args);
                break;

            // Commandes liées aux employés
            case "employee": // Sous-menu pour inviter, virer (obsolète si GUI est principal)
                handleEmployeeSubCommand(player, args); // Peut-être déprécier ou simplifier
                break;
            case "leave": // Quitter une entreprise (Employé)
                handleLeaveCommand(player, args); // Pourrait être juste /entreprise leave <NomEntreprise>
                break;
            case "kick": // Virer un employé (Gérant, via GUI ou /entreprise kick <NomEnt> <NomEmp>)
                handleKickCommandDirect(player, args);
                break;

            // Actions liées aux invitations et contrats
            case "accepter": // Accepter une invitation d'employé ou un contrat de gérance
                // La logique d'accepter un contrat de gérance est validercreation
                entrepriseLogic.handleAccepterCommand(player); // Pour invitations d'employé
                break;
            case "refuser": // Refuser une invitation d'employé ou un contrat de gérance
                // La logique de refuser un contrat de gérance est annulercreation
                entrepriseLogic.handleRefuserCommand(player); // Pour invitations d'employé
                break;
            case "validercreation": // Gérant accepte le contrat proposé par le maire
                entrepriseLogic.validerCreationEntreprise(player);
                break;
            case "annulercreation": // Gérant refuse le contrat
                entrepriseLogic.refuserCreationEntreprise(player);
                break;

            // Commandes financières (souvent via GUI, mais direct possible)
            case "withdraw": // Retirer de l'argent (Gérant)
                handleWithdrawCommandDirect(player, args);
                break;
            case "deposit": // Déposer de l'argent (Gérant/Employé)
                handleDepositCommandDirect(player, args);
                break;

            // Commandes utilitaires et admin
            case "gui": // Ouvrir le menu principal
                entrepriseGUI.openMainMenu(player);
                break;
            case "primenews": // Voir les notifications de primes (pas un paiement)
                handlePrimeNewsCommand(player);
                break;
            case "admin":
                handleAdminCommand(player, args);
                break;

            // Commandes internes (appelées par GUI via clic, ne devraient pas être tapées par l'utilisateur)
            // Elles sont gardées pour la compatibilité avec l'ancien GUI ou si vous voulez les exposer.
            case "selectwithdraw": // Interne GUI
            case "selectdeposit":  // Interne GUI
            case "confirmkick":    // Interne GUI
            case "confirmleave":   // Interne GUI
                player.sendMessage(ChatColor.YELLOW + "Cette commande est normalement utilisée via l'interface graphique.");
                // Vous pouvez ajouter la logique ici si vous voulez qu'elles soient aussi directes
                // Exemple pour selectwithdraw (similaire à handleWithdrawCommandDirect mais sans lister)
                if (subCommand.equals("selectwithdraw") && args.length > 1) {
                    plugin.getChatListener().attendreMontantRetrait(player, args[1]);
                } else if (subCommand.equals("selectdeposit") && args.length > 1) {
                    plugin.getChatListener().attendreMontantDepot(player, args[1]);
                } else if (subCommand.equals("confirmkick") && args.length > 2) {
                    entrepriseLogic.kickEmploye(player, args[1], args[2]);
                } else if (subCommand.equals("confirmleave") && args.length > 1) {
                    entrepriseLogic.leaveEntreprise(player, args[1]);
                }
                break;

            default:
                player.sendMessage(ChatColor.RED + "Commande EntrepriseManager inconnue. Utilisez /entreprise gui");
                return false;
        }
        return true;
    }

    private void handlePrimeNewsCommand(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Récupération de vos notifications de primes et de gérance...");
        entrepriseLogic.envoyerPrimesDifferreesEmployes(player);
        entrepriseLogic.envoyerPrimesDifferreesGerants(player);
    }

    private void handleCreateCommand(Player player, String[] args) {
        if (!player.hasPermission("entreprisemanager.create.command")) { // Permission spécifique pour la commande
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission d'utiliser cette commande. Utilisez le GUI.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise create <NomGérantCible> <TypeEntreprise>");
            return;
        }
        // ... (logique de création comme dans le GUI.openCreateEntreprise... ou la version précédente)
        // S'assurer que le joueur est maire, que le type est valide, etc.
        String nomGerantCible = args[1];
        String typeEntreprise = args[2];
        Player gerantCible = Bukkit.getPlayerExact(nomGerantCible);

        if (!entrepriseLogic.estMaire(player)) {
            player.sendMessage(ChatColor.RED + "Seuls les maires peuvent proposer la création d'entreprises.");
            return;
        }
        String villeDuMaire = entrepriseLogic.getTownNameFromPlayer(player);
        if (villeDuMaire == null) { /* ... */ return; }
        if (gerantCible == null || !gerantCible.isOnline()) { /* ... */ return; }
        if (!entrepriseLogic.getTypesEntreprise().contains(typeEntreprise)) { /* ... */ return; }
        if (!entrepriseLogic.estMembreDeLaVille(nomGerantCible, villeDuMaire)) { /* ... */ return; }
        // ... autres vérifications de EntrepriseManagerLogic ...

        String nomPropose = typeEntreprise + "_" + nomGerantCible.substring(0, Math.min(nomGerantCible.length(), 4)) + "_" + (new Random().nextInt(9000) + 1000);
        String siretPropose = entrepriseLogic.generateSiret();
        entrepriseLogic.proposerCreationEntreprise(player, gerantCible, typeEntreprise, villeDuMaire, nomPropose, siretPropose);
    }
    private void handleDeleteCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise delete <NomDeVotreEntreprise>");
            return;
        }
        String nomEntreprise = args[1];
        entrepriseLogic.supprimerEntreprise(player, nomEntreprise); // La méthode gère les permissions et messages
    }
    private void handleRenameCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise rename <AncienNom> <NouveauNom>");
            return;
        }
        entrepriseLogic.renameEntreprise(player, args[1], args[2]); // La méthode gère permissions et messages
    }

    private void handleEmployeeSubCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise employee <invite|setprime|...> ...");
            return;
        }
        String action = args[1].toLowerCase();
        if (action.equals("invite")) {
            if (args.length < 4) { // /entreprise employee invite <NomEntreprise> <NomJoueur>
                player.sendMessage(ChatColor.RED + "Usage: /entreprise employee invite <NomDeVotreEntreprise> <NomDuJoueur>");
                return;
            }
            // ... (logique d'invitation)
            String nomEnt = args[2];
            String nomJoueurInvite = args[3];
            Player joueurInvite = Bukkit.getPlayerExact(nomJoueurInvite);
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEnt);

            if (entreprise == null || !entreprise.getGerant().equalsIgnoreCase(player.getName())) { /* ... */ return; }
            if (joueurInvite == null) { /* ... */ return; }
            entrepriseLogic.inviterEmploye(player, nomEnt, joueurInvite);

        } else if (action.equals("setprime")) {
            if (args.length < 5) { // /entreprise employee setprime <NomEntreprise> <NomEmploye> <Montant>
                player.sendMessage(ChatColor.RED + "Usage: /entreprise employee setprime <NomEnt> <NomEmp> <Montant>");
                return;
            }
            String nomEnt = args[2];
            String nomEmp = args[3];
            try {
                double montant = Double.parseDouble(args[4]);
                EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEnt);
                if (entreprise != null && entreprise.getGerant().equalsIgnoreCase(player.getName()) && entreprise.getEmployes().contains(nomEmp)) {
                    entrepriseLogic.definirPrime(nomEnt, nomEmp, montant);
                    player.sendMessage(ChatColor.GREEN + "Prime de " + nomEmp + " pour '" + nomEnt + "' définie à " + montant + "€/h.");
                    Player empPlayer = Bukkit.getPlayerExact(nomEmp);
                    if (empPlayer != null && empPlayer.isOnline()) {
                        empPlayer.sendMessage(ChatColor.GOLD + "Votre prime horaire pour '" + nomEnt + "' est maintenant de " + montant + "€/h.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de définir la prime (entreprise non trouvée, pas gérant, ou employé invalide).");
                }
            } catch (NumberFormatException e){
                player.sendMessage(ChatColor.RED + "Montant invalide.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Action '" + action + "' pour employé non reconnue.");
        }
    }
    private void handleLeaveCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise leave <NomEntreprise>");
            return;
        }
        entrepriseLogic.leaveEntreprise(player, args[1]); // La méthode gère messages et logique
    }

    private void handleKickCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise kick <NomEntreprise> <NomEmploye>");
            return;
        }
        entrepriseLogic.kickEmploye(player, args[1], args[2]); // La méthode gère messages et logique
    }


    private void handleWithdrawCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise withdraw <NomEntreprise> <Montant>");
            return;
        }
        String nomEntreprise = args[1];
        try {
            double montant = Double.parseDouble(args[2]);
            entrepriseLogic.retirerArgent(player, nomEntreprise, montant); // La méthode gère messages et logique
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Le montant est invalide.");
        }
    }
    private void handleDepositCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise deposit <NomEntreprise> <Montant>");
            return;
        }
        String nomEntreprise = args[1];
        try {
            double montant = Double.parseDouble(args[2]);
            entrepriseLogic.deposerArgent(player, nomEntreprise, montant); // La méthode gère messages et logique
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Le montant est invalide.");
        }
    }


    private void handleListCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise list <NomDeLaVilleOu*>");
            return;
        }
        String ville = args[1];
        if (ville.equals("*")) {
            // Lister toutes les villes qui ont au moins une entreprise
            Set<String> villesAvecEntreprises = new HashSet<>();
            for(EntrepriseManagerLogic.Entreprise e : entrepriseLogic.getEntreprises()){
                villesAvecEntreprises.add(e.getVille());
            }
            if(villesAvecEntreprises.isEmpty()){
                player.sendMessage(ChatColor.YELLOW + "Aucune entreprise enregistrée sur le serveur.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Villes avec des entreprises: " + String.join(", ", villesAvecEntreprises));
            }
        } else {
            entrepriseLogic.listEntreprises(player, ville);
        }
    }

    private void handleInfoCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise info <NomDeLEntreprise>");
            return;
        }
        String nomEntreprise = args[1];
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Aucune entreprise trouvée avec le nom '" + nomEntreprise + "'.");
            return;
        }
        entrepriseGUI.displayEntrepriseInfo(player, entreprise); // Réutiliser le formatage du GUI
    }

    // Dans EntrepriseCommandHandler.java - handleAdminCommand
    private void handleAdminCommand(Player player, String[] args) {
        if (!player.hasPermission("entreprisemanager.admin")) { // Permission générale pour /entreprise admin
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission pour les commandes admin.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise admin <forcepay|reload>");
            return;
        }

        String subAdminCommand = args[1].toLowerCase();
        switch (subAdminCommand) {
            case "forcepay":
                adminForcePayCommand(player); // Appelle la méthode qui contient la logique et les permissions spécifiques
                break;
            case "reload":
                adminReloadCommand(player); // Appelle la méthode qui contient la logique et les permissions spécifiques
                break;
            default:
                player.sendMessage(ChatColor.RED + "Sous-commande admin inconnue: " + subAdminCommand);
                break;
        }
    }


    private void adminReloadCommand(Player player) {
        if (!player.hasPermission("entreprisemanager.admin.reload")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de recharger le plugin.");
            return;
        }
        EntrepriseManager pluginInstance = entrepriseLogic.getPlugin();
        if (pluginInstance != null) {
            pluginInstance.reloadPlugin(); // Appelle la méthode reloadPlugin() de la classe principale
            player.sendMessage(ChatColor.GREEN + "Plugin EntrepriseManager et données rechargés.");
        } else {
            player.sendMessage(ChatColor.RED + "Erreur critique : Impossible de recharger, instance du plugin non trouvée.");
        }
    }

    private void adminForcePayCommand(Player player) {
        if (!player.hasPermission("entreprisemanager.admin.forcepay")) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas la permission de forcer le cycle de paiement.");
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "Forçage du cycle de paiement horaire global...");
        entrepriseLogic.traiterChiffreAffairesHoraire(); // Doit être public dans EntrepriseManagerLogic
        entrepriseLogic.payerPrimesHorairesAuxEmployes();  // Doit être public
        entrepriseLogic.payerAllocationChomageHoraire(); // Doit être public
        player.sendMessage(ChatColor.GREEN + "Cycle de paiement horaire global forcé avec succès !");
    }
}