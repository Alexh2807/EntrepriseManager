package com.gravityyfh.entreprisemanager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class EntrepriseCommandHandler implements CommandExecutor {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseGUI entrepriseGUI;
    private final CVManager cvManager;
    private final EntrepriseManager plugin;

    public EntrepriseCommandHandler(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic, EntrepriseGUI entrepriseGUI, CVManager cvManager) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        this.entrepriseGUI = entrepriseGUI;
        this.cvManager = cvManager;
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

        // AJOUT DE LOGS POUR LE DÉBOGAGE
        plugin.getLogger().log(Level.INFO, "[DEBUG] Commande reçue par " + player.getName() + ": /" + label + " " + String.join(" ", args));

        String subCommand = args[0].toLowerCase().trim(); // Utilisation de trim() par sécurité

        plugin.getLogger().log(Level.INFO, "[DEBUG] Sous-commande identifiée: '" + subCommand + "'");

        switch (subCommand) {
            case "create":
                handleCreateCommand(player, args);
                break;
            case "delete":
                handleDeleteCommand(player, args);
                break;
            case "info":
                handleInfoCommand(player, args);
                break;
            case "list":
                handleListCommand(player, args);
                break;
            case "rename":
                handleRenameCommandDirect(player, args);
                break;
            case "employee":
            case "employe":
                handleEmployeeSubCommand(player, args);
                break;
            case "leave":
                handleLeaveCommand(player, args);
                break;
            case "kick":
                handleKickCommandDirect(player, args);
                break;
            case "accepter":
                entrepriseLogic.handleAccepterCommand(player);
                break;
            case "refuser":
                entrepriseLogic.handleRefuserCommand(player);
                break;
            case "validercreation":
                entrepriseLogic.validerCreationEntreprise(player);
                break;
            case "annulercreation":
                entrepriseLogic.refuserCreationEntreprise(player);
                break;
            case "withdraw":
                handleWithdrawCommandDirect(player, args);
                break;
            case "deposit":
                handleDepositCommandDirect(player, args);
                break;
            case "gui":
                entrepriseGUI.openMainMenu(player);
                break;
            case "primenews":
                handlePrimeNewsCommand(player);
                break;
            case "admin":
                handleAdminCommand(player, args);
                break;
            case "stats":
                handleStatsCommand(player, args);
                break;
            case "cv":
                handleCVCommand(player, args);
                break;
            case "shop":
            case "boutique":
                handleShopCommand(player, args); // Cette ligne reste la même
                break;
            default:
                player.sendMessage(ChatColor.RED + "Commande inconnue. Utilisez /entreprise gui");
                return false;
        }
        return true;
    }

    /**
     * Rassemble les arguments pour former un seul nom (ex: pour les noms d'entreprise).
     * @param args Les arguments de la commande.
     * @param startIndex L'index de début (inclus).
     * @param endIndex L'index de fin (exclus).
     * @return Le nom assemblé.
     */
    private String joinArguments(String[] args, int startIndex, int endIndex) {
        return Arrays.stream(args, startIndex, endIndex).collect(Collectors.joining(" "));
    }

    private void handleShopCommand(Player player, String[] args) {
        // La logique pour la sous-commande /entreprise shop
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise shop <NomDeLEntreprise>");
            return;
        }

        String nomEntreprise = Arrays.stream(args, 1, args.length).collect(Collectors.joining(" "));
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);

        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' n'a pas été trouvée.");
            return;
        }

        // Vérifie si le joueur est le gérant
        if (!entreprise.getGerantUUID().equals(player.getUniqueId().toString())) {
            player.sendMessage(ChatColor.RED + "Vous devez être le gérant de cette entreprise pour gérer ses boutiques.");
            return;
        }

        // APPEL AU NOUVEAU MENU DE LISTE
        plugin.getShopGUI().openShopListMenu(player, entreprise, 0); // Ouvre à la page 0
    }

    private void handleDeleteCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise delete <NomEntreprise>");
            return;
        }
        String nomEntreprise = joinArguments(args, 1, args.length);
        entrepriseLogic.supprimerEntreprise(player, nomEntreprise);
    }

    private void handleInfoCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise info <NomEntreprise>");
            return;
        }
        String nomEntreprise = joinArguments(args, 1, args.length);
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEntreprise);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée.");
            return;
        }
        entrepriseGUI.displayEntrepriseInfo(player, entreprise);
    }

    private void handleLeaveCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise leave <NomEntreprise>");
            return;
        }
        String nomEntreprise = joinArguments(args, 1, args.length);
        entrepriseLogic.leaveEntreprise(player, nomEntreprise);
    }

    private void handleKickCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise kick <NomEntreprise> <NomEmploye>");
            return;
        }
        // Le nom du joueur est le dernier argument
        String nomEmploye = args[args.length - 1];
        // Le nom de l'entreprise est tout ce qui se trouve entre "kick" et le nom de l'employé
        String nomEntreprise = joinArguments(args, 1, args.length - 1);
        entrepriseLogic.kickEmploye(player, nomEntreprise, nomEmploye);
    }

    private void handleWithdrawCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise withdraw <NomEntreprise> <Montant>");
            return;
        }
        try {
            String montantStr = args[args.length - 1];
            double amount = Double.parseDouble(montantStr);
            String nomEntreprise = joinArguments(args, 1, args.length - 1);
            if(amount <= 0) { player.sendMessage(ChatColor.RED + "Le montant doit être positif."); return; }
            entrepriseLogic.retirerArgent(player, nomEntreprise, amount);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Montant invalide.");
        }
    }

    private void handleDepositCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise deposit <NomEntreprise> <Montant>");
            return;
        }
        try {
            String montantStr = args[args.length - 1];
            double amount = Double.parseDouble(montantStr);
            String nomEntreprise = joinArguments(args, 1, args.length - 1);
            if(amount <= 0) { player.sendMessage(ChatColor.RED + "Le montant doit être positif."); return; }
            entrepriseLogic.deposerArgent(player, nomEntreprise, amount);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Montant invalide.");
        }
    }

    // Le reste de vos méthodes reste identique...
    private void handlePrimeNewsCommand(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Vérification des notifications...");
        entrepriseLogic.envoyerPrimesDifferreesEmployes(player);
        entrepriseLogic.envoyerPrimesDifferreesGerants(player);
    }

    private void handleCreateCommand(Player player, String[] args) {
        if (!player.hasPermission("entreprisemanager.create.command")) {
            player.sendMessage(ChatColor.RED + "Permission refusée. Utilisez /entreprise gui.");
            return;
        }
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise create <NomGérantCible> <TypeEntreprise>");
            return;
        }
        String nomGerantCible = args[1];
        String typeEntreprise = args[2];
        Player gerantCiblePlayer = Bukkit.getPlayerExact(nomGerantCible);

        if (!entrepriseLogic.estMaire(player)) { player.sendMessage(ChatColor.RED + "Seuls les maires peuvent créer."); return; }
        String villeDuMaire = entrepriseLogic.getTownNameFromPlayer(player);
        if (villeDuMaire == null) { player.sendMessage(ChatColor.RED + "Ville introuvable."); return; }
        if (gerantCiblePlayer == null || !gerantCiblePlayer.isOnline()) { player.sendMessage(ChatColor.RED + "Joueur gérant cible hors ligne ou introuvable."); return; }
        if (!entrepriseLogic.getTypesEntreprise().contains(typeEntreprise)) { player.sendMessage(ChatColor.RED + "Type d'entreprise invalide: '" + typeEntreprise + "'."); return; }

        String nomPropose = typeEntreprise + "_" + nomGerantCible.substring(0, Math.min(nomGerantCible.length(), 4)) + "_" + (new Random().nextInt(9000) + 1000);
        String siretPropose = entrepriseLogic.generateSiret();

        entrepriseLogic.proposerCreationEntreprise(player, gerantCiblePlayer, typeEntreprise, villeDuMaire, nomPropose, siretPropose);
    }

    private void handleListCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise list <NomVille|*>");
            return;
        }
        String ville = args[1];
        if (ville.equals("*")) {
            Set<String> villes = entrepriseLogic.getEntreprises().stream()
                    .map(EntrepriseManagerLogic.Entreprise::getVille)
                    .filter(v -> v != null && !v.isEmpty())
                    .collect(Collectors.toSet());
            if (villes.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Aucune entreprise enregistrée sur le serveur.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Villes avec des entreprises: " + ChatColor.AQUA + String.join(ChatColor.GRAY + ", " + ChatColor.AQUA, villes));
                player.sendMessage(ChatColor.GRAY+"Utilisez /entreprise list <NomVille> pour voir les détails.");
            }
        } else {
            entrepriseLogic.listEntreprises(player, ville);
        }
    }

    // Pour la commande /entreprise rename, la gestion de noms avec espaces est complexe.
    // Il est recommandé d'utiliser le GUI pour cette action.
    // La méthode actuelle ne supportera que les noms sans espaces.
    private void handleRenameCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise rename <AncienNom> <NouveauNom>");
            player.sendMessage(ChatColor.GRAY + "Pour les noms avec espaces, veuillez utiliser le GUI.");
            return;
        }
        entrepriseLogic.renameEntreprise(player, args[1], args[2]);
    }

    private void handleEmployeeSubCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise employee <invite|setprime> ...");
            return;
        }
        String action = args[1].toLowerCase();

        if (action.equals("invite")) {
            if (args.length < 4) { player.sendMessage(ChatColor.RED + "Usage: /entreprise employee invite <NomEntreprise> <NomJoueur>"); return; }
            String nomJoueurInvite = args[args.length - 1];
            String nomEnt = joinArguments(args, 2, args.length - 1);
            Player joueurInvite = Bukkit.getPlayerExact(nomJoueurInvite);
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEnt);

            if (entreprise == null || !entreprise.getGerant().equalsIgnoreCase(player.getName())) { player.sendMessage(ChatColor.RED+"Entreprise/Gérant invalide."); return; }
            if (joueurInvite == null || !joueurInvite.isOnline()) { player.sendMessage(ChatColor.RED+"Joueur à inviter non trouvé ou hors ligne."); return; }
            if (joueurInvite.equals(player)) { player.sendMessage(ChatColor.RED+"Vous ne pouvez pas vous auto-inviter."); return; }
            if (entrepriseLogic.getNomEntrepriseDuMembre(joueurInvite.getName()) != null) { player.sendMessage(ChatColor.RED + joueurInvite.getName() + " est déjà membre d'une entreprise."); return; }

            entrepriseLogic.inviterEmploye(player, nomEnt, joueurInvite);

        } else if (action.equals("setprime")) {
            if (args.length < 5) { player.sendMessage(ChatColor.RED + "Usage: /entreprise employee setprime <NomEnt> <NomEmp> <Montant>"); return; }
            try {
                double montant = Double.parseDouble(args[args.length - 1]);
                String nomEmp = args[args.length - 2];
                String nomEnt = joinArguments(args, 2, args.length - 2);

                if(montant < 0) { player.sendMessage(ChatColor.RED + "Le montant de la prime doit être positif ou nul."); return;}
                entrepriseLogic.definirPrime(nomEnt, nomEmp, montant);
                player.sendMessage(ChatColor.GREEN + "Prime de " + nomEmp + " définie à " + String.format("%,.2f", montant) + "€/h pour '" + nomEnt + "'.");
                Player empPlayer = Bukkit.getPlayerExact(nomEmp);
                if (empPlayer != null && empPlayer.isOnline()) {
                    empPlayer.sendMessage(ChatColor.GOLD + "Votre prime pour '" + nomEnt + "' est maintenant de " + String.format("%,.2f", montant) + "€/h.");
                }

            } catch (NumberFormatException e){
                player.sendMessage(ChatColor.RED + "Montant invalide.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Action employé '" + action + "' inconnue. Utilisez invite ou setprime.");
        }
    }

    private void handleAdminCommand(Player player, String[] args) {
        if (!player.hasPermission("entreprisemanager.admin")) {
            player.sendMessage(ChatColor.RED + "Permission refusée.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise admin <forcepay|reload|forcesave|cleandisplay>");
            return;
        }
        String subCmd = args[1].toLowerCase();
        switch (subCmd) {
            case "forcepay": adminForcePayCommand(player); break;
            case "reload": adminReloadCommand(player); break;
            case "forcesave": adminForceSaveCommand(player); break;
            case "cleandisplay": adminCleanDisplayCommand(player); break;
            default: player.sendMessage(ChatColor.RED + "Sous-commande admin inconnue: " + subCmd); break;
        }
    }

    private void adminReloadCommand(Player player) {
        if (!player.hasPermission("entreprisemanager.admin.reload")) {
            player.sendMessage(ChatColor.RED + "Permission refusée.");
            return;
        }
        if (plugin != null) {
            plugin.reloadPluginData();
            player.sendMessage(ChatColor.GREEN + "Plugin EntrepriseManager et ses données ont été rechargés.");
        } else {
            player.sendMessage(ChatColor.RED + "Erreur: Instance du plugin non trouvée.");
        }
    }

    private void adminForcePayCommand(Player player) {
        if (!player.hasPermission("entreprisemanager.admin.forcepay")) { player.sendMessage(ChatColor.RED + "Permission refusée."); return; }
        player.sendMessage(ChatColor.YELLOW + "Forçage manuel du cycle horaire (CA, Primes, Charges, Chômage)...");
        entrepriseLogic.traiterChiffreAffairesHoraire();
        entrepriseLogic.payerPrimesHorairesAuxEmployes();
        entrepriseLogic.payerChargesSalarialesHoraires();
        entrepriseLogic.payerAllocationChomageHoraire();
        player.sendMessage(ChatColor.GREEN + "Cycle horaire forcé ! Vérifiez la console pour les détails.");
    }

    private void adminForceSaveCommand(Player player) {
        if (!player.hasPermission("entreprisemanager.admin.forcesave")) { player.sendMessage(ChatColor.RED + "Permission refusée."); return; }
        entrepriseLogic.saveEntreprises();
        player.sendMessage(ChatColor.GREEN + "Données des entreprises sauvegardées manuellement !");
    }

    private void adminCleanDisplayCommand(Player player) {
        if (!player.hasPermission("entreprisemanager.admin.cleandisplay")) {
            player.sendMessage(ChatColor.RED + "Permission refusée.");
            return;
        }

        boolean removed = plugin.getShopManager().removeTargetedDisplayItem(player);
        if (removed) {
            player.sendMessage(ChatColor.GREEN + "[Succès] L'entité Display ciblée a été supprimée.");
        } else {
            player.sendMessage(ChatColor.RED + "[Erreur] Vous ne visez aucune entité Display.");
        }
    }

    private void handleStatsCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise stats <info|transactions|employe|employes|production> ...");
            return;
        }
        String statsSubCmd = args[1].toLowerCase();

        switch (statsSubCmd) {
            case "info":
                if (args.length < 3) { player.sendMessage(ChatColor.RED + "Usage: /entreprise stats info <NomEnt>"); return; }
                handleStatsInfoCommand(player, joinArguments(args, 2, args.length));
                break;
            case "transactions":
                if (args.length < 3) { player.sendMessage(ChatColor.RED + "Usage: /entreprise stats transactions <NomEnt> [lignes]"); return; }
                int limit = 10;
                String nomEntTransac = joinArguments(args, 2, args.length);
                if (args.length > 3) {
                    try {
                        limit = Integer.parseInt(args[args.length - 1]);
                        nomEntTransac = joinArguments(args, 2, args.length - 1);
                    } catch (NumberFormatException e) {
                        // l'argument final n'est pas un nombre, on le considère comme partie du nom
                    }
                }
                handleStatsTransactionsCommand(player, nomEntTransac, limit);
                break;
            case "employe":
            case "employee":
                if (args.length < 4) { player.sendMessage(ChatColor.RED + "Usage: /entreprise stats employe <NomEnt> <NomEmp>"); return; }
                handleStatsEmployeDetailCommand(player, joinArguments(args, 2, args.length -1), args[args.length - 1]);
                break;
            case "employes":
                if (args.length < 3) { player.sendMessage(ChatColor.RED + "Usage: /entreprise stats employes <NomEnt>"); return; }
                handleStatsListEmployesCommand(player, joinArguments(args, 2, args.length));
                break;
            case "production":
                if (args.length < 5) {
                    player.sendMessage(ChatColor.RED + "Usage: /entreprise stats production <NomEnt> <NomEmp|global> <type_action> [periode]");
                    player.sendMessage(ChatColor.GRAY + "Types d'action: broken, crafted, placed");
                    player.sendMessage(ChatColor.GRAY + "Périodes: 3h, 24h, 7j, 30j, total (défaut: 24h)");
                    return;
                }
                handleStatsProductionCommand(player, args);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Sous-commande stats inconnue: " + statsSubCmd);
                break;
        }
    }

    private void handleStatsInfoCommand(Player player, String nomEntreprise) {
        EntrepriseManagerLogic.Entreprise ent = entrepriseLogic.getEntreprise(nomEntreprise);
        if (ent == null) { player.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée."); return; }
        boolean isMember = ent.getGerant().equalsIgnoreCase(player.getName()) || ent.getEmployes().contains(player.getName());
        if (!isMember && !player.hasPermission("entreprisemanager.admin.viewallstats")) { player.sendMessage(ChatColor.RED + "Permission refusée."); return; }

        player.sendMessage(ChatColor.GOLD + "--- Stats Financières: " + ChatColor.AQUA + ent.getNom() + ChatColor.GOLD + " ---");
        player.sendMessage(ChatColor.YELLOW + "Solde Actuel: " + ChatColor.GREEN + String.format("%,.2f", ent.getSolde()) + "€");
        player.sendMessage(ChatColor.YELLOW + "CA Brut (Total historique): " + ChatColor.DARK_GREEN + String.format("%,.2f", ent.getChiffreAffairesTotal()) + "€");
        player.sendMessage(ChatColor.YELLOW + "CA Potentiel Horaire Actuel: " + ChatColor.AQUA + String.format("%,.2f", entrepriseLogic.getActiviteHoraireValeurPour(ent.getNom())) + "€");
        LocalDateTime end = LocalDateTime.now();
        double pl24h = ent.calculateProfitLoss(end.minusDays(1), end);
        double pl7d = ent.calculateProfitLoss(end.minusWeeks(1), end);
        double pl30d = ent.calculateProfitLoss(end.minusMonths(1), end);
        ChatColor c24 = pl24h >= 0 ? ChatColor.GREEN : ChatColor.RED;
        ChatColor c7 = pl7d >= 0 ? ChatColor.GREEN : ChatColor.RED;
        ChatColor c30 = pl30d >= 0 ? ChatColor.GREEN : ChatColor.RED;
        player.sendMessage(ChatColor.YELLOW + "Profit/Perte Opérationnel:");
        player.sendMessage(ChatColor.GRAY + " - Dernières 24h: " + c24 + String.format("%+, .2f", pl24h) + "€");
        player.sendMessage(ChatColor.GRAY + " - Dernière semaine: " + c7 + String.format("%+, .2f", pl7d) + "€");
        player.sendMessage(ChatColor.GRAY + " - Dernier mois: " + c30 + String.format("%+, .2f", pl30d) + "€");
        player.sendMessage(ChatColor.GOLD + "------------------------------------------");
    }

    private void handleStatsTransactionsCommand(Player player, String nomEntreprise, int limit) {
        limit = Math.max(1, Math.min(limit, 50)); // Clamp value
        EntrepriseManagerLogic.Entreprise ent = entrepriseLogic.getEntreprise(nomEntreprise);
        if (ent == null) { player.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée."); return; }
        boolean isMember = ent.getGerant().equalsIgnoreCase(player.getName()) || ent.getEmployes().contains(player.getName());
        if (!isMember && !player.hasPermission("entreprisemanager.admin.viewallstats")) { player.sendMessage(ChatColor.RED + "Permission refusée."); return; }

        List<EntrepriseManagerLogic.Transaction> txs = entrepriseLogic.getTransactionsPourEntreprise(nomEntreprise, limit);
        if (txs.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "Aucune transaction enregistrée pour " + nomEntreprise + "."); return; }
        player.sendMessage(ChatColor.GOLD + "--- " + txs.size() + " Dernières Transactions: " + ChatColor.AQUA + ent.getNom() + ChatColor.GOLD + " ---");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        for (EntrepriseManagerLogic.Transaction tx : txs) {
            ChatColor amountColor; String amountPrefix = "";
            if (tx.type == EntrepriseManagerLogic.TransactionType.DEPOSIT || tx.type.isOperationalIncome()) { amountColor = ChatColor.GREEN; amountPrefix = "+"; }
            else if (tx.type == EntrepriseManagerLogic.TransactionType.WITHDRAWAL || tx.type.isOperationalExpense()) { amountColor = ChatColor.RED; }
            else { amountColor = ChatColor.YELLOW; }
            if (Math.abs(tx.amount) < 0.01) amountColor = ChatColor.GRAY;
            String amountStr = String.format("%s%s%.2f€", amountColor, (tx.amount >= 0 ? amountPrefix : ""), tx.amount);
            player.sendMessage(String.format("%s[%s] %s%s: %s %s(%s) %sPar: %s%s", ChatColor.GRAY, tx.timestamp.format(fmt), ChatColor.YELLOW, tx.type.getDisplayName(), amountStr, ChatColor.DARK_GRAY, tx.description, ChatColor.BLUE, ChatColor.WHITE, tx.initiatedBy));
        }
        player.sendMessage(ChatColor.GOLD + "---------------------------------------------");
    }

    private void handleStatsListEmployesCommand(Player player, String nomEntreprise) {
        EntrepriseManagerLogic.Entreprise ent = entrepriseLogic.getEntreprise(nomEntreprise);
        if (ent == null) { player.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée."); return; }
        boolean isMember = ent.getGerant().equalsIgnoreCase(player.getName()) || ent.getEmployes().contains(player.getName());
        if (!isMember && !player.hasPermission("entreprisemanager.admin.viewallstats")) { player.sendMessage(ChatColor.RED + "Permission refusée."); return; }

        Collection<EntrepriseManagerLogic.EmployeeActivityRecord> records = ent.getEmployeeActivityRecords().values();
        if (records.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "Aucune donnée d'employé à afficher pour " + nomEntreprise + "."); return; }
        player.sendMessage(ChatColor.GOLD + "--- Aperçu Stats Employés: " + ChatColor.AQUA + ent.getNom() + ChatColor.GOLD + " ---");
        records.stream()
                .sorted(Comparator.comparing(r -> r.employeeName.toLowerCase()))
                .forEach(rec -> {
                    String primeStr = String.format("%,.2f€/h", ent.getPrimePourEmploye(rec.employeeId.toString()));
                    String status = rec.isActive() ? ChatColor.GREEN + "Actif" : ChatColor.GRAY + "Inactif";
                    player.sendMessage(String.format("%s- %s%s: %sAncienneté: %s%s, %sPrime: %s%s, %sCA Généré: %s%,.2f€, %sStatut: %s", ChatColor.GRAY, ChatColor.AQUA, rec.employeeName, ChatColor.DARK_GRAY, ChatColor.WHITE, rec.getFormattedSeniority(), ChatColor.DARK_GRAY, ChatColor.YELLOW, primeStr, ChatColor.DARK_GRAY, ChatColor.GREEN, rec.totalValueGenerated, ChatColor.DARK_GRAY, status));
                });
        player.sendMessage(ChatColor.GOLD + "---------------------------------------------");
        player.sendMessage(ChatColor.GRAY + "Pour détails: /entreprise stats employe <NomEnt> <NomEmp>");
    }


    private void handleStatsEmployeDetailCommand(Player player, String nomEntreprise, String nomEmploye) {
        EntrepriseManagerLogic.Entreprise ent = entrepriseLogic.getEntreprise(nomEntreprise);
        if (ent == null) { player.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée."); return; }
        OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(nomEmploye);
        if (!offlineEmp.hasPlayedBefore() && !offlineEmp.isOnline() && ent.getEmployeeActivityRecord(offlineEmp.getUniqueId()) == null ) {
            player.sendMessage(ChatColor.RED + "Employé '" + nomEmploye + "' introuvable ou jamais enregistré dans cette entreprise."); return;
        }
        UUID empUUID = offlineEmp.getUniqueId();
        EntrepriseManagerLogic.EmployeeActivityRecord rec = ent.getEmployeeActivityRecord(empUUID);
        boolean isOwn = player.getUniqueId().equals(empUUID);
        boolean isGerant = ent.getGerant().equalsIgnoreCase(player.getName());
        if (!isOwn && !isGerant && !player.hasPermission("entreprisemanager.admin.viewallstats")) { player.sendMessage(ChatColor.RED + "Permission refusée."); return; }
        if (rec == null) { player.sendMessage(ChatColor.YELLOW + "Aucune donnée d'activité pour " + nomEmploye + " dans cette entreprise."); return; }

        player.sendMessage(ChatColor.GOLD + "--- Détails " + ChatColor.AQUA + rec.employeeName + ChatColor.GOLD + " (" + ent.getNom() + ") ---");
        player.sendMessage(ChatColor.YELLOW + "Ancienneté: " + ChatColor.WHITE + rec.getFormattedSeniority());
        player.sendMessage(ChatColor.YELLOW + "Valeur Générée (Total): " + ChatColor.GREEN + String.format("%,.2f", rec.totalValueGenerated) + "€");
        player.sendMessage(ChatColor.YELLOW + "Prime Actuelle: " + ChatColor.WHITE + String.format("%,.2f", ent.getPrimePourEmploye(rec.employeeId.toString())) + "€/h");

        if (rec.actionsPerformedCount.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "Aucune action spécifique comptée.");
        } else {
            player.sendMessage(ChatColor.YELLOW + "Actions spécifiques (Total historique):");
            rec.actionsPerformedCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> {
                        String[] parts = entry.getKey().split(":");
                        String actionType = formatActionType(parts[0]);
                        String materialName = parts.length > 1 ? formatMaterialName(parts[1]) : "";
                        player.sendMessage(ChatColor.GRAY + "  - " + actionType + (materialName.isEmpty() ? "" : " de " + ChatColor.ITALIC + materialName) + ": " + ChatColor.WHITE + entry.getValue());
                    });
            if(rec.actionsPerformedCount.size() > 10) player.sendMessage(ChatColor.GRAY + "  (et autres...)");
        }
        player.sendMessage(ChatColor.YELLOW + "Statut Session: " + (rec.isActive() ? ChatColor.GREEN + "Active" : ChatColor.GRAY + "Inactive"));
        if (rec.lastActivityTime != null) {
            player.sendMessage(ChatColor.YELLOW + "Dernière activité enregistrée: " + ChatColor.WHITE + rec.lastActivityTime.format(DateTimeFormatter.ofPattern("dd/MM/yy HH:mm")));
        }
        player.sendMessage(ChatColor.GOLD + "------------------------------------------");
        player.sendMessage(ChatColor.GRAY+"Pour les stats de production: /entreprise stats production " + ent.getNom() + " " + rec.employeeName + " <type> [periode]");
    }

    private void handleStatsProductionCommand(Player player, String[] args) {
        String nomEntreprise = args[2];
        EntrepriseManagerLogic.Entreprise ent = entrepriseLogic.getEntreprise(nomEntreprise);
        if (ent == null) { player.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée."); return; }

        String cibleArg = args[3]; String actionTypeArg = args[4].toLowerCase();
        String periodeStr = (args.length > 5) ? args[5].toLowerCase() : "24h";

        EntrepriseManagerLogic.DetailedActionType actionTypeFilter;
        switch (actionTypeArg) {
            case "broken": case "break": case "cassé": case "casses": actionTypeFilter = EntrepriseManagerLogic.DetailedActionType.BLOCK_BROKEN; break;
            case "crafted": case "craft": case "fabriqué": case "fabriques": actionTypeFilter = EntrepriseManagerLogic.DetailedActionType.ITEM_CRAFTED; break;
            case "placed": case "place": case "posé": case "poses": actionTypeFilter = EntrepriseManagerLogic.DetailedActionType.BLOCK_PLACED; break;
            default: player.sendMessage(ChatColor.RED + "Type d'action invalide: '" + actionTypeArg + "'."); return;
        }

        UUID targetEmpUUID = null; boolean globalStats = false; String titleCibleName;

        if (cibleArg.equalsIgnoreCase("global")) {
            if (!ent.getGerant().equalsIgnoreCase(player.getName()) && !player.hasPermission("entreprisemanager.admin.viewallstats")) { player.sendMessage(ChatColor.RED + "Permission refusée."); return; }
            globalStats = true; titleCibleName = "Global";
        } else {
            OfflinePlayer targetEmpOffline = Bukkit.getOfflinePlayer(cibleArg);
            if (!targetEmpOffline.hasPlayedBefore() && !targetEmpOffline.isOnline() && ent.getEmployeeActivityRecord(targetEmpOffline.getUniqueId()) == null) { player.sendMessage(ChatColor.RED + "Employé '" + cibleArg + "' non trouvé."); return; }
            targetEmpUUID = targetEmpOffline.getUniqueId(); titleCibleName = targetEmpOffline.getName() != null ? targetEmpOffline.getName() : cibleArg;
            boolean isOwn = player.getUniqueId().equals(targetEmpUUID);
            if (!isOwn && !ent.getGerant().equalsIgnoreCase(player.getName()) && !player.hasPermission("entreprisemanager.admin.viewallstats")) { player.sendMessage(ChatColor.RED + "Permission refusée."); return; }
        }

        LocalDateTime end = LocalDateTime.now(); LocalDateTime start;
        switch (periodeStr) {
            case "3h": start = end.minusHours(3); break;
            case "24h": case "jour": start = end.minusDays(1); break;
            case "7j": case "semaine": start = end.minusWeeks(1); break;
            case "30j": case "mois": start = end.minusMonths(1); break;
            case "total":
                if (globalStats) start = ent.getGlobalProductionLog().stream().min(Comparator.comparing(r -> r.timestamp)).map(r -> r.timestamp).orElse(end);
                else { EntrepriseManagerLogic.EmployeeActivityRecord rec = ent.getEmployeeActivityRecord(targetEmpUUID); start = (rec != null && rec.joinDate != null) ? rec.joinDate : LocalDateTime.MIN; }
                break;
            default: player.sendMessage(ChatColor.RED + "Période invalide: '" + periodeStr + "'."); return;
        }

        Map<Material, Integer> stats;
        if (globalStats) stats = entrepriseLogic.getCompanyProductionStatsForPeriod(nomEntreprise, start, end, actionTypeFilter);
        else stats = entrepriseLogic.getEmployeeProductionStatsForPeriod(nomEntreprise, targetEmpUUID, start, end, actionTypeFilter);

        player.sendMessage(ChatColor.GOLD + "--- Stats Production: " + ChatColor.AQUA + titleCibleName + ChatColor.GOLD + " (" + ent.getNom() + " - " + ChatColor.YELLOW + actionTypeFilter.getDisplayName() + ChatColor.GOLD + " - " + periodeStr + ") ---");
        if (stats.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "Aucune production de ce type pour cette période.");
        } else {
            stats.entrySet().stream().sorted(Map.Entry.<Material, Integer>comparingByValue().reversed()).limit(20)
                    .forEach(entry -> player.sendMessage(ChatColor.GREEN + formatMaterialName(entry.getKey().name()) + ": " + ChatColor.WHITE + String.format("%,d", entry.getValue())));
            if(stats.size() > 20) player.sendMessage(ChatColor.GRAY + "  (et "+(stats.size()-20)+" autres...)");
        }
        player.sendMessage(ChatColor.GOLD + "-----------------------------------------------");
    }

    private void handleCVCommand(Player player, String[] args) {
        if (args.length < 2) { sendCVHelp(player); return; }
        String cvSubCommand = args[1].toLowerCase();
        switch (cvSubCommand) {
            case "show": case "montrer":
                if (args.length < 3) { player.sendMessage(ChatColor.RED + "Usage: /entreprise cv show <nom_du_joueur_cible>"); return; }
                Player targetPlayerShow = Bukkit.getPlayerExact(args[2]);
                if (targetPlayerShow == null || !targetPlayerShow.isOnline()) { player.sendMessage(ChatColor.RED + "Le joueur '" + args[2] + "' n'est pas en ligne."); return; }
                cvManager.requestShareCV(player, targetPlayerShow);
                break;
            case "accept": case "accepter":
                cvManager.handleAcceptCV(player);
                break;
            case "refuse": case "refuser":
                cvManager.handleRefuseCV(player);
                break;
            default: player.sendMessage(ChatColor.RED + "Sous-commande CV inconnue."); sendCVHelp(player); break;
        }
    }

    private void sendCVHelp(Player player){
        player.sendMessage(ChatColor.YELLOW + "--- Aide CV Entreprise ---");
        player.sendMessage(ChatColor.AQUA + "/entreprise cv show <joueur>" + ChatColor.GRAY + " - Proposer de montrer votre CV.");
        player.sendMessage(ChatColor.AQUA + "/entreprise cv accepter" + ChatColor.GRAY + " - Accepter une demande de CV.");
        player.sendMessage(ChatColor.AQUA + "/entreprise cv refuser" + ChatColor.GRAY + " - Refuser une demande de CV.");
        player.sendMessage(ChatColor.YELLOW + "--------------------------");
    }

    private String formatActionType(String actionKey) {
        if (actionKey == null || actionKey.isEmpty()) return "Action";
        return Arrays.stream(actionKey.toLowerCase().replace("_", " ").split(" ")).map(word -> word.substring(0, 1).toUpperCase() + word.substring(1)).collect(Collectors.joining(" "));
    }

    private String formatMaterialName(String materialKey) {
        if (materialKey == null || materialKey.isEmpty()) return "";
        try {
            Material mat = Material.matchMaterial(materialKey);
            if (mat != null) {}
        } catch (IllegalArgumentException e) {}
        return Arrays.stream(materialKey.toLowerCase().replace("_", " ").split(" ")).map(word -> word.substring(0, 1).toUpperCase() + word.substring(1)).collect(Collectors.joining(" "));
    }
}
