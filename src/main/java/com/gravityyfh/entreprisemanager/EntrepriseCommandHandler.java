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
import java.util.stream.Collectors;

public class EntrepriseCommandHandler implements CommandExecutor {

    private final EntrepriseManagerLogic entrepriseLogic;
    private final EntrepriseGUI entrepriseGUI;
    private final CVManager cvManager; // Remplacer PlayerCVGUI par CVManager
    private final EntrepriseManager plugin;

    // Le constructeur prend maintenant CVManager
    public EntrepriseCommandHandler(EntrepriseManager plugin, EntrepriseManagerLogic entrepriseLogic, EntrepriseGUI entrepriseGUI, CVManager cvManager) {
        this.plugin = plugin;
        this.entrepriseLogic = entrepriseLogic;
        this.entrepriseGUI = entrepriseGUI;
        this.cvManager = cvManager; // Stocker l'instance de CVManager
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
            // ... autres cas ...
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
            case "accepter": // Accepter invitation entreprise
                entrepriseLogic.handleAccepterCommand(player);
                break;
            case "refuser": // Refuser invitation entreprise
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
            case "cv": // Commande pour le CV
                handleCVCommand(player, args); // Déléguer au nouveau handler
                break;
            // ... cas internes ...
            default:
                player.sendMessage(ChatColor.RED + "Commande inconnue. Utilisez /entreprise gui");
                return false;
        }
        return true;
    }

    // --- Handlers existants (inchangés, sauf si besoin de passer cvManager) ---
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

    private void handleDeleteCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise delete <NomEntreprise>");
            return;
        }
        entrepriseLogic.supprimerEntreprise(player, args[1]);
    }

    private void handleRenameCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise rename <AncienNom> <NouveauNom>");
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
            String nomEnt = args[2];
            String nomJoueurInvite = args[3];
            Player joueurInvite = Bukkit.getPlayerExact(nomJoueurInvite);
            EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEnt);

            if (entreprise == null || !entreprise.getGerant().equalsIgnoreCase(player.getName())) { player.sendMessage(ChatColor.RED+"Entreprise/Gérant invalide."); return; }
            if (joueurInvite == null || !joueurInvite.isOnline()) { player.sendMessage(ChatColor.RED+"Joueur à inviter non trouvé ou hors ligne."); return; }
            if (joueurInvite.equals(player)) { player.sendMessage(ChatColor.RED+"Vous ne pouvez pas vous auto-inviter."); return; }
            if (entrepriseLogic.getNomEntrepriseDuMembre(joueurInvite.getName()) != null) { player.sendMessage(ChatColor.RED + joueurInvite.getName() + " est déjà membre d'une entreprise."); return; }

            entrepriseLogic.inviterEmploye(player, nomEnt, joueurInvite);

        } else if (action.equals("setprime")) {
            if (args.length < 5) { player.sendMessage(ChatColor.RED + "Usage: /entreprise employee setprime <NomEnt> <NomEmp> <Montant>"); return; }
            String nomEnt = args[2];
            String nomEmp = args[3];
            try {
                double montant = Double.parseDouble(args[4]);
                if(montant < 0) { player.sendMessage(ChatColor.RED + "Le montant de la prime doit être positif ou nul."); return;}

                EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(nomEnt);
                OfflinePlayer offlineEmp = Bukkit.getOfflinePlayer(nomEmp); // Important pour avoir l'UUID même si offline

                if (entreprise != null && entreprise.getGerant().equalsIgnoreCase(player.getName()) && (entreprise.getEmployes().contains(nomEmp) || offlineEmp.hasPlayedBefore())) { // Vérifie aussi si le joueur existe

                    // Utiliser l'UUID est plus fiable pour stocker/récupérer la prime
                    entrepriseLogic.definirPrime(nomEnt, nomEmp, montant); // La méthode logique doit gérer la conversion nom -> UUID si nécessaire
                    player.sendMessage(ChatColor.GREEN + "Prime de " + nomEmp + " définie à " + String.format("%,.2f", montant) + "€/h pour '" + nomEnt + "'.");

                    Player empPlayer = offlineEmp.getPlayer(); // Récupérer le joueur s'il est en ligne
                    if (empPlayer != null && empPlayer.isOnline()) {
                        empPlayer.sendMessage(ChatColor.GOLD + "Votre prime pour '" + nomEnt + "' est maintenant de " + String.format("%,.2f", montant) + "€/h.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "Impossible de définir la prime (vérifiez entreprise/gérant/employé).");
                }
            } catch (NumberFormatException e){
                player.sendMessage(ChatColor.RED + "Montant invalide.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Action employé '" + action + "' inconnue. Utilisez invite ou setprime.");
        }
    }

    private void handleLeaveCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise leave <NomEntreprise>");
            return;
        }
        entrepriseLogic.leaveEntreprise(player, args[1]);
    }

    private void handleKickCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise kick <NomEntreprise> <NomEmploye>");
            return;
        }
        entrepriseLogic.kickEmploye(player, args[1], args[2]);
    }

    private void handleWithdrawCommandDirect(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise withdraw <NomEntreprise> <Montant>");
            return;
        }
        try {
            double amount = Double.parseDouble(args[2]);
            if(amount <= 0) { player.sendMessage(ChatColor.RED + "Le montant doit être positif."); return;}
            entrepriseLogic.retirerArgent(player, args[1], amount);
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
            double amount = Double.parseDouble(args[2]);
            if(amount <= 0) { player.sendMessage(ChatColor.RED + "Le montant doit être positif."); return;}
            entrepriseLogic.deposerArgent(player, args[1], amount);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Montant invalide.");
        }
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
                    .filter(v -> v != null && !v.isEmpty()) // Filtrer les villes null ou vides
                    .collect(Collectors.toSet());
            if (villes.isEmpty()) {
                player.sendMessage(ChatColor.YELLOW + "Aucune entreprise enregistrée sur le serveur.");
            } else {
                player.sendMessage(ChatColor.YELLOW + "Villes avec des entreprises: " + ChatColor.AQUA + String.join(ChatColor.GRAY + ", " + ChatColor.AQUA, villes));
                player.sendMessage(ChatColor.GRAY+"Utilisez /entreprise list <NomVille> pour voir les détails.");
            }
        } else {
            // Utiliser la méthode listEntreprises qui envoie un message formaté
            entrepriseLogic.listEntreprises(player, ville);
        }
    }

    private void handleInfoCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise info <NomEntreprise>");
            player.sendMessage(ChatColor.GRAY + "Utilisez /entreprise stats info <Nom> pour les stats financières.");
            return;
        }
        EntrepriseManagerLogic.Entreprise entreprise = entrepriseLogic.getEntreprise(args[1]);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Entreprise '" + args[1] + "' non trouvée.");
            return;
        }
        // Afficher les infos via la méthode dédiée du GUI (qui ferme l'inv et affiche dans le chat)
        entrepriseGUI.displayEntrepriseInfo(player, entreprise);
    }

    private void handleAdminCommand(Player player, String[] args) {
        if (!player.hasPermission("entreprisemanager.admin")) {
            player.sendMessage(ChatColor.RED + "Permission refusée.");
            return;
        }
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise admin <forcepay|reload|forcesave>");
            return;
        }
        String subCmd = args[1].toLowerCase();
        switch (subCmd) {
            case "forcepay": adminForcePayCommand(player); break;
            case "reload": adminReloadCommand(player); break;
            case "forcesave": adminForceSaveCommand(player); break;
            default: player.sendMessage(ChatColor.RED + "Sous-commande admin inconnue: " + subCmd); break;
        }
    }

    private void adminReloadCommand(Player player) {
        if (!player.hasPermission("entreprisemanager.admin.reload")) {
            player.sendMessage(ChatColor.RED + "Permission refusée.");
            return;
        }
        if (plugin != null) { // 'plugin' ici est bien une instance de EntrepriseManager
            plugin.reloadPluginData(); // <<< UTILISEZ LE NOM CORRECT DE VOTRE MÉTHODE
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

    private void handleStatsCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Usage: /entreprise stats <info|transactions|employe|employes|production> ...");
            return;
        }
        String statsSubCmd = args[1].toLowerCase();

        switch (statsSubCmd) {
            case "info":
                if (args.length < 3) { player.sendMessage(ChatColor.RED + "Usage: /entreprise stats info <NomEnt>"); return; }
                handleStatsInfoCommand(player, args[2]);
                break;
            case "transactions":
                if (args.length < 3) { player.sendMessage(ChatColor.RED + "Usage: /entreprise stats transactions <NomEnt> [lignes]"); return; }
                int limit = (args.length > 3) ? parseIntOrDefault(args[3], 10, 1, 50) : 10;
                handleStatsTransactionsCommand(player, args[2], limit);
                break;
            case "employe":
            case "employee":
                if (args.length < 4) { player.sendMessage(ChatColor.RED + "Usage: /entreprise stats employe <NomEnt> <NomEmp>"); return; }
                handleStatsEmployeDetailCommand(player, args[2], args[3]);
                break;
            case "employes":
                if (args.length < 3) { player.sendMessage(ChatColor.RED + "Usage: /entreprise stats employes <NomEnt>"); return; }
                handleStatsListEmployesCommand(player, args[2]);
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

    private int parseIntOrDefault(String input, int defaultValue, int min, int max) {
        try {
            int value = Integer.parseInt(input);
            return Math.max(min, Math.min(value, max)); // Clamp value between min and max
        } catch (NumberFormatException e) {
            return defaultValue;
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
        EntrepriseManagerLogic.Entreprise ent = entrepriseLogic.getEntreprise(nomEntreprise);
        if (ent == null) { player.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée."); return; }
        boolean isMember = ent.getGerant().equalsIgnoreCase(player.getName()) || ent.getEmployes().contains(player.getName());
        // Autoriser les membres à voir les transactions pour la transparence ? Oui.
        if (!isMember && !player.hasPermission("entreprisemanager.admin.viewallstats")) { player.sendMessage(ChatColor.RED + "Permission refusée."); return; }

        List<EntrepriseManagerLogic.Transaction> txs = entrepriseLogic.getTransactionsPourEntreprise(nomEntreprise, limit);
        if (txs.isEmpty()) { player.sendMessage(ChatColor.YELLOW + "Aucune transaction enregistrée pour " + nomEntreprise + "."); return; }
        player.sendMessage(ChatColor.GOLD + "--- " + txs.size() + " Dernières Transactions: " + ChatColor.AQUA + ent.getNom() + ChatColor.GOLD + " ---");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        for (EntrepriseManagerLogic.Transaction tx : txs) {
            // Affiner la couleur basée sur le type et le montant
            ChatColor amountColor;
            String amountPrefix = "";
            if (tx.type == EntrepriseManagerLogic.TransactionType.DEPOSIT || tx.type.isOperationalIncome()) {
                amountColor = ChatColor.GREEN;
                amountPrefix = "+";
            } else if (tx.type == EntrepriseManagerLogic.TransactionType.WITHDRAWAL || tx.type.isOperationalExpense()) {
                amountColor = ChatColor.RED;
                // Le montant est déjà négatif pour les dépenses/retraits dans la logique de Transaction
            } else {
                amountColor = ChatColor.YELLOW; // Pour les cas inattendus
            }
            // Si le montant est 0, utiliser gris
            if (Math.abs(tx.amount) < 0.01) amountColor = ChatColor.GRAY;

            String amountStr = String.format("%s%s%.2f€", amountColor, (tx.amount >= 0 ? amountPrefix : ""), tx.amount);

            player.sendMessage(String.format("%s[%s] %s%s: %s %s(%s) %sPar: %s%s",
                    ChatColor.GRAY, tx.timestamp.format(fmt),
                    ChatColor.YELLOW, tx.type.getDisplayName(), amountStr,
                    ChatColor.DARK_GRAY, tx.description,
                    ChatColor.BLUE, ChatColor.WHITE, tx.initiatedBy));
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
                .sorted(Comparator.comparing(r -> r.employeeName.toLowerCase())) // Trier par nom
                .forEach(rec -> {
                    String primeStr = String.format("%,.2f€/h", ent.getPrimePourEmploye(rec.employeeId.toString()));
                    String status = rec.isActive() ? ChatColor.GREEN + "Actif" : ChatColor.GRAY + "Inactif";
                    player.sendMessage(String.format("%s- %s%s: %sAncienneté: %s%s, %sPrime: %s%s, %sCA Généré: %s%,.2f€, %sStatut: %s",
                            ChatColor.GRAY, ChatColor.AQUA, rec.employeeName,
                            ChatColor.DARK_GRAY, ChatColor.WHITE, rec.getFormattedSeniority(),
                            ChatColor.DARK_GRAY, ChatColor.YELLOW, primeStr,
                            ChatColor.DARK_GRAY, ChatColor.GREEN, rec.totalValueGenerated,
                            ChatColor.DARK_GRAY, status));
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
                    .limit(10) // Limiter pour ne pas spammer
                    .forEach(entry -> {
                        String[] parts = entry.getKey().split(":");
                        String actionType = parts.length > 0 ? formatActionType(parts[0]) : "Action";
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
        // Args: 0=stats, 1=production, 2=NomEnt, 3=NomEmp|global, 4=type_action, 5=periode(opt)
        String nomEntreprise = args[2];
        EntrepriseManagerLogic.Entreprise ent = entrepriseLogic.getEntreprise(nomEntreprise);
        if (ent == null) { player.sendMessage(ChatColor.RED + "Entreprise '" + nomEntreprise + "' non trouvée."); return; }

        String cibleArg = args[3];
        String actionTypeArg = args[4].toLowerCase();
        String periodeStr = (args.length > 5) ? args[5].toLowerCase() : "24h"; // Default 24h

        EntrepriseManagerLogic.DetailedActionType actionTypeFilter;
        switch (actionTypeArg) {
            case "broken": case "break": case "cassé": case "casses": actionTypeFilter = EntrepriseManagerLogic.DetailedActionType.BLOCK_BROKEN; break;
            case "crafted": case "craft": case "fabriqué": case "fabriques": actionTypeFilter = EntrepriseManagerLogic.DetailedActionType.ITEM_CRAFTED; break;
            case "placed": case "place": case "posé": case "poses": actionTypeFilter = EntrepriseManagerLogic.DetailedActionType.BLOCK_PLACED; break;
            default: player.sendMessage(ChatColor.RED + "Type d'action invalide: '" + actionTypeArg + "'. Utilisez broken, crafted, ou placed."); return;
        }

        UUID targetEmpUUID = null;
        boolean globalStats = false;
        String titleCibleName;

        if (cibleArg.equalsIgnoreCase("global")) {
            if (!ent.getGerant().equalsIgnoreCase(player.getName()) && !player.hasPermission("entreprisemanager.admin.viewallstats")) {
                player.sendMessage(ChatColor.RED + "Permission refusée pour les statistiques globales."); return;
            }
            globalStats = true;
            titleCibleName = "Global";
        } else {
            OfflinePlayer targetEmpOffline = Bukkit.getOfflinePlayer(cibleArg);
            if (!targetEmpOffline.hasPlayedBefore() && !targetEmpOffline.isOnline() && ent.getEmployeeActivityRecord(targetEmpOffline.getUniqueId()) == null) {
                player.sendMessage(ChatColor.RED + "Employé '" + cibleArg + "' non trouvé ou sans activité dans cette entreprise."); return;
            }
            targetEmpUUID = targetEmpOffline.getUniqueId();
            titleCibleName = targetEmpOffline.getName() != null ? targetEmpOffline.getName() : cibleArg;
            boolean isOwn = player.getUniqueId().equals(targetEmpUUID);
            if (!isOwn && !ent.getGerant().equalsIgnoreCase(player.getName()) && !player.hasPermission("entreprisemanager.admin.viewallstats")) {
                player.sendMessage(ChatColor.RED + "Permission refusée pour voir les stats de cet employé."); return;
            }
        }

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start;
        switch (periodeStr) {
            case "3h": start = end.minusHours(3); break;
            case "24h": case "jour": start = end.minusDays(1); break;
            case "7j": case "semaine": start = end.minusWeeks(1); break;
            case "30j": case "mois": start = end.minusMonths(1); break;
            case "total":
                if (globalStats) start = ent.getGlobalProductionLog().stream().min(Comparator.comparing(r -> r.timestamp)).map(r -> r.timestamp).orElse(end);
                else {
                    EntrepriseManagerLogic.EmployeeActivityRecord rec = ent.getEmployeeActivityRecord(targetEmpUUID);
                    start = (rec != null && rec.joinDate != null) ? rec.joinDate : LocalDateTime.MIN;
                }
                break;
            default: player.sendMessage(ChatColor.RED + "Période invalide: '" + periodeStr + "'."); return;
        }

        Map<Material, Integer> stats;
        if (globalStats) stats = entrepriseLogic.getCompanyProductionStatsForPeriod(nomEntreprise, start, end, actionTypeFilter);
        else stats = entrepriseLogic.getEmployeeProductionStatsForPeriod(nomEntreprise, targetEmpUUID, start, end, actionTypeFilter);

        player.sendMessage(ChatColor.GOLD + "--- Stats Production: " + ChatColor.AQUA + titleCibleName +
                ChatColor.GOLD + " (" + ent.getNom() + " - " + ChatColor.YELLOW + actionTypeFilter.getDisplayName() +
                ChatColor.GOLD + " - " + periodeStr + ") ---");

        if (stats.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "Aucune production de ce type trouvée pour cette période.");
        } else {
            stats.entrySet().stream()
                    .sorted(Map.Entry.<Material, Integer>comparingByValue().reversed())
                    .limit(20) // Limiter pour le chat
                    .forEach(entry -> player.sendMessage(ChatColor.GREEN + formatMaterialName(entry.getKey().name()) + ": " + ChatColor.WHITE + String.format("%,d", entry.getValue())));
            if(stats.size() > 20) player.sendMessage(ChatColor.GRAY + "  (et "+(stats.size()-20)+" autres...)");
        }
        player.sendMessage(ChatColor.GOLD + "-----------------------------------------------");
    }


    // --- Nouveau Handler pour la commande /entreprise cv ---
    private void handleCVCommand(Player player, String[] args) {
        // args[0] = "cv"
        if (args.length < 2) {
            sendCVHelp(player);
            return;
        }

        String cvSubCommand = args[1].toLowerCase();
        switch (cvSubCommand) {
            case "show":
            case "montrer":
                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /entreprise cv show <nom_du_joueur_cible>");
                    return;
                }
                Player targetPlayerShow = Bukkit.getPlayerExact(args[2]);
                if (targetPlayerShow == null || !targetPlayerShow.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Le joueur '" + args[2] + "' n'est pas en ligne ou n'existe pas.");
                    return;
                }
                cvManager.requestShareCV(player, targetPlayerShow); // Appel à CVManager
                break;
            case "accept":
            case "accepter":
                cvManager.handleAcceptCV(player); // Appel à CVManager
                break;
            case "refuse":
            case "refuser":
                cvManager.handleRefuseCV(player); // Appel à CVManager
                break;
            default:
                player.sendMessage(ChatColor.RED + "Sous-commande CV inconnue.");
                sendCVHelp(player);
                break;
        }
    }

    private void sendCVHelp(Player player){
        player.sendMessage(ChatColor.YELLOW + "--- Aide CV Entreprise ---");
        player.sendMessage(ChatColor.AQUA + "/entreprise cv show <joueur>" + ChatColor.GRAY + " - Proposer de montrer votre CV.");
        player.sendMessage(ChatColor.AQUA + "/entreprise cv accepter" + ChatColor.GRAY + " - Accepter une demande de CV.");
        player.sendMessage(ChatColor.AQUA + "/entreprise cv refuser" + ChatColor.GRAY + " - Refuser une demande de CV.");
        player.sendMessage(ChatColor.YELLOW + "--------------------------");
    }

    // --- Méthodes utilitaires pour le formatage (similaires à PlayerCVGUI) ---
    private String formatActionType(String actionKey) {
        if (actionKey == null || actionKey.isEmpty()) return "Action";
        return Arrays.stream(actionKey.toLowerCase().replace("_", " ").split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private String formatMaterialName(String materialKey) {
        if (materialKey == null || materialKey.isEmpty()) return "";
        try {
            Material mat = Material.matchMaterial(materialKey);
            if (mat != null) {
                // Vous pouvez ajouter des noms "amicaux" ici si vous voulez
                // switch(mat) { case RAW_IRON_ORE: return "Minerai de Fer Brut"; ... }
            }
        } catch (IllegalArgumentException e) { /* Ignorer si le matériau n'est pas standard */ }

        // Formatage par défaut
        return Arrays.stream(materialKey.toLowerCase().replace("_", " ").split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));
    }

}