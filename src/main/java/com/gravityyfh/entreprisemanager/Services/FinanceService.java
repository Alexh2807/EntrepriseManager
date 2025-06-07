package com.gravityyfh.entreprisemanager.Services;

import com.gravityyfh.entreprisemanager.EntrepriseManager;
import com.gravityyfh.entreprisemanager.EntrepriseManagerLogic;
import com.gravityyfh.entreprisemanager.Models.EmployeeActivityRecord;
import com.gravityyfh.entreprisemanager.Models.Entreprise;
import com.gravityyfh.entreprisemanager.Models.Transaction;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FinanceService {

    private final EntrepriseManager plugin;
    private final EntrepriseManagerLogic logic;
    private final NotificationService notificationService;

    public FinanceService(EntrepriseManagerLogic logic, NotificationService notificationService) {
        this.logic = logic;
        this.plugin = logic.plugin;
        this.notificationService = notificationService;
    }

    public void processHourlyRevenue() {
        double pourcentageTaxes = plugin.getConfig().getDouble("finance.pourcentage-taxes", 20.0);
        boolean modified = false;

        for (Map.Entry<String, Double> entry : new HashMap<>(logic.getActiviteHoraireValeur()).entrySet()) {
            String nomEntreprise = entry.getKey();
            double caBrutHoraire = entry.getValue();
            if (caBrutHoraire <= 0) {
                logic.getActiviteHoraireValeur().put(nomEntreprise, 0.0);
                continue;
            }

            Entreprise entreprise = logic.getEntreprise(nomEntreprise);
            if (entreprise == null) {
                logic.getActiviteHoraireValeur().remove(nomEntreprise);
                continue;
            }

            modified = true;
            double ancienSolde = entreprise.getSolde();
            double taxesCalculees = caBrutHoraire * (pourcentageTaxes / 100.0);
            double caNetHoraire = caBrutHoraire - taxesCalculees;

            entreprise.setSolde(ancienSolde + caNetHoraire);
            entreprise.setChiffreAffairesTotal(entreprise.getChiffreAffairesTotal() + caBrutHoraire);
            entreprise.addTransaction(new Transaction(Transaction.TransactionType.REVENUE, caBrutHoraire, "Revenu horaire brut", "System"));
            if (taxesCalculees > 0) {
                entreprise.addTransaction(new Transaction(Transaction.TransactionType.TAXES, taxesCalculees, "Impôts (" + pourcentageTaxes + "%) sur CA horaire", "System"));
            }

            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            String messageDetails = String.format("&bSolde: &f%.2f€ &7| &bCA Brut: &f+%.2f€ &7| &cTaxes: &f-%.2f€ &7| &aCA Net: &f+%.2f€ &7| &bNouv. Solde: &f&l%.2f€",
                    ancienSolde, caBrutHoraire, taxesCalculees, caNetHoraire, entreprise.getSolde());

            if (gerantPlayer != null && gerantPlayer.isOnline()) {
                gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&l[Rapport Horaire] &e" + entreprise.getNom()));
                gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', messageDetails));
            } else {
                String resume = String.format("Rapport Horaire '%s': CA Net +%.2f€. Solde: %.2f€.", entreprise.getNom(), caNetHoraire, entreprise.getSolde());
                notificationService.ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.GREEN + resume);
            }
            logic.getActiviteHoraireValeur().put(nomEntreprise, 0.0);
        }
        if (modified) logic.saveData();
    }

    public void payHourlyWages() {
        boolean modified = false;
        for (Entreprise entreprise : logic.getEntreprisesMap().values()) {
            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            OfflinePlayer offlineGerant = Bukkit.getOfflinePlayer(UUID.fromString(entreprise.getGerantUUID()));

            for (String employeNom : entreprise.getEmployes()) {
                OfflinePlayer employeOffline = Bukkit.getOfflinePlayer(employeNom);
                if (employeOffline == null || !employeOffline.hasPlayedBefore()) continue;

                UUID employeUUID = employeOffline.getUniqueId();
                double primeConfigurée = entreprise.getPrimePourEmploye(employeUUID.toString());
                if (primeConfigurée <= 0) continue;

                EmployeeActivityRecord activity = entreprise.getEmployeeActivityRecord(employeUUID);
                if (activity == null || !activity.isActive()) continue;

                if (entreprise.getSolde() >= primeConfigurée) {
                    EconomyResponse er = EntrepriseManager.getEconomy().depositPlayer(employeOffline, primeConfigurée);
                    if (er.transactionSuccess()) {
                        double soldeAvant = entreprise.getSolde();
                        entreprise.setSolde(soldeAvant - primeConfigurée);
                        entreprise.addTransaction(new Transaction(Transaction.TransactionType.PRIMES, primeConfigurée, "Prime horaire: " + employeNom, "System"));
                        modified = true;

                        String msgEmploye = String.format("&aPrime horaire reçue: &e%.2f€&a de '&6%s&a'.", primeConfigurée, entreprise.getNom());
                        String msgGerant = String.format("&bPrime versée à &3%s&b: &e%.2f€&b. Solde: &e%.2f€ &7-> &e%.2f€", employeNom, primeConfigurée, soldeAvant, entreprise.getSolde());

                        Player onlineEmp = employeOffline.getPlayer();
                        if (onlineEmp != null) onlineEmp.sendMessage(ChatColor.translateAlternateColorCodes('&', msgEmploye));
                        else notificationService.ajouterMessageEmployeDifferre(employeUUID.toString(), ChatColor.translateAlternateColorCodes('&', msgEmploye), entreprise.getNom(), primeConfigurée);

                        if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', msgGerant));
                        else if (offlineGerant.hasPlayedBefore()) notificationService.ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.translateAlternateColorCodes('&', msgGerant));

                    } else {
                        if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.RED + "Erreur versement prime à " + employeNom + ": " + er.errorMessage);
                    }
                } else {
                    String msgEchecEmp = String.format("&cL'entreprise '&6%s&c' n'a pas pu verser votre prime de &e%.2f€&c.", entreprise.getNom(), primeConfigurée);
                    String msgEchecGer = String.format("&cSolde insuffisant (&e%.2f€&c) pour prime de &3%s&c (&e%.2f€&c).", entreprise.getSolde(), employeNom, primeConfigurée);

                    Player onlineEmp = employeOffline.getPlayer();
                    if (onlineEmp != null) onlineEmp.sendMessage(ChatColor.translateAlternateColorCodes('&', msgEchecEmp));
                    else notificationService.ajouterMessageEmployeDifferre(employeUUID.toString(), ChatColor.translateAlternateColorCodes('&', msgEchecEmp), entreprise.getNom(), 0);

                    if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', msgEchecGer));
                    else if (offlineGerant.hasPlayedBefore()) notificationService.ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.translateAlternateColorCodes('&', msgEchecGer));
                }
            }
        }
        if (modified) logic.saveData();
    }

    public void payPayrollTaxes() {
        double chargeParEmploye = plugin.getConfig().getDouble("finance.charge-salariale-par-employe-horaire", 5.0);
        boolean actifsSeulement = plugin.getConfig().getBoolean("finance.charges-sur-employes-actifs-seulement", false);

        if (chargeParEmploye <= 0) return;
        boolean modified = false;

        for (Entreprise entreprise : logic.getEntreprisesMap().values()) {
            long nbEmployesConcernes = actifsSeulement ?
                    entreprise.getEmployeeActivityRecords().values().stream().filter(EmployeeActivityRecord::isActive).count() :
                    entreprise.getEmployes().size();

            if (nbEmployesConcernes == 0) continue;

            double totalCharges = nbEmployesConcernes * chargeParEmploye;
            Player gerantPlayer = Bukkit.getPlayerExact(entreprise.getGerant());
            String employesType = actifsSeulement ? "actifs" : "total";

            if (entreprise.getSolde() >= totalCharges) {
                double soldeAvant = entreprise.getSolde();
                entreprise.setSolde(soldeAvant - totalCharges);
                entreprise.addTransaction(new Transaction(Transaction.TransactionType.PAYROLL_TAX, totalCharges, "Charges salariales (" + nbEmployesConcernes + " emp. " + employesType + ")", "System"));
                modified = true;
                String msgSucces = String.format("&aCharges salariales horaires (&b%d&a emp. %s): &e-%.2f€&a. Solde: &e%.2f€ &7-> &e%.2f€",
                        nbEmployesConcernes, employesType, totalCharges, soldeAvant, entreprise.getSolde());

                if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', msgSucces));
                else notificationService.ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.translateAlternateColorCodes('&', msgSucces));

            } else {
                String msgEchec = String.format("&cSolde insuffisant (&e%.2f€&c) pour charges salariales (&e%.2f€&c pour %d emp. %s).",
                        entreprise.getSolde(), totalCharges, nbEmployesConcernes, employesType);

                if (gerantPlayer != null && gerantPlayer.isOnline()) gerantPlayer.sendMessage(ChatColor.translateAlternateColorCodes('&', msgEchec));
                else notificationService.ajouterMessageGerantDifferre(entreprise.getGerantUUID(), ChatColor.translateAlternateColorCodes('&', msgEchec));
            }
        }
        if (modified) logic.saveData();
    }

    public void payUnemploymentBenefits() {
        double montantAllocation = plugin.getConfig().getDouble("finance.allocation-chomage-horaire", 200.0);
        if (montantAllocation <= 0) return;

        for (Player joueurConnecte : Bukkit.getOnlinePlayers()) {
            if (logic.getEntrepriseDuJoueur(joueurConnecte) == null) {
                EconomyResponse er = EntrepriseManager.getEconomy().depositPlayer(joueurConnecte, montantAllocation);
                if (er.transactionSuccess()) {
                    joueurConnecte.sendMessage(ChatColor.translateAlternateColorCodes('&', String.format("&6[Alloc. Chômage] &a+%.2f€", montantAllocation)));
                }
            }
        }
    }

    public void depositMoney(Player player, String nomEntreprise, double montant) {
        Entreprise entreprise = logic.getEntreprise(nomEntreprise);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "L'entreprise '" + nomEntreprise + "' n'a pas été trouvée.");
            return;
        }
        if (!entreprise.getGerant().equalsIgnoreCase(player.getName()) && !entreprise.getEmployes().contains(player.getName())) {
            player.sendMessage(ChatColor.RED + "Seuls les membres de l'entreprise peuvent y déposer de l'argent.");
            return;
        }
        if (montant <= 0) {
            player.sendMessage(ChatColor.RED + "Le montant du dépôt doit être positif.");
            return;
        }
        if (!EntrepriseManager.getEconomy().has(player, montant)) {
            player.sendMessage(ChatColor.RED + "Vous n'avez pas assez d'argent sur votre compte personnel.");
            return;
        }

        double soldeMaxActuel = logic.getEnterpriseService().getLimiteMaxSoldeActuelle(entreprise);
        if (entreprise.getSolde() + montant > soldeMaxActuel) {
            double montantAutorise = soldeMaxActuel - entreprise.getSolde();
            if (montantAutorise <= 0) {
                player.sendMessage(ChatColor.RED + "L'entreprise a atteint son solde maximum actuel (" + String.format("%,.2f", soldeMaxActuel) + "€).");
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "Le montant a été ajusté pour ne pas dépasser le solde maximum de l'entreprise (" + String.format("%,.2f", soldeMaxActuel) + "€).");
            montant = montantAutorise;
        }

        EconomyResponse response = EntrepriseManager.getEconomy().withdrawPlayer(player, montant);
        if (response.transactionSuccess()) {
            entreprise.setSolde(entreprise.getSolde() + montant);
            entreprise.addTransaction(new Transaction(Transaction.TransactionType.DEPOSIT, montant, "Dépôt par " + player.getName(), player.getName()));
            logic.saveData();
            player.sendMessage(ChatColor.GREEN + String.format("%,.2f", montant) + "€ déposés dans '" + nomEntreprise + "'. Nouveau solde de l'entreprise : " + String.format("%,.2f", entreprise.getSolde()) + "€.");
        } else {
            player.sendMessage(ChatColor.RED + "Erreur lors du retrait de votre compte : " + response.errorMessage);
        }
    }

    public void withdrawMoney(Player player, String nomEntreprise, double montant) {
        Entreprise entreprise = logic.getEntreprise(nomEntreprise);
        if (entreprise == null) {
            player.sendMessage(ChatColor.RED + "Ent. '" + nomEntreprise + "' non trouvée.");
            return;
        }
        if (!entreprise.getGerant().equalsIgnoreCase(player.getName())) {
            player.sendMessage(ChatColor.RED + "Seul le gérant peut retirer.");
            return;
        }
        if (montant <= 0) {
            player.sendMessage(ChatColor.RED + "Montant doit être positif.");
            return;
        }
        if (entreprise.getSolde() < montant) {
            player.sendMessage(ChatColor.RED + "Solde ent. (" + String.format("%,.2f€", entreprise.getSolde()) + ") insuffisant.");
            return;
        }
        EconomyResponse response = EntrepriseManager.getEconomy().depositPlayer(player, montant);
        if (response.transactionSuccess()) {
            entreprise.setSolde(entreprise.getSolde() - montant);
            entreprise.addTransaction(new Transaction(Transaction.TransactionType.WITHDRAWAL, montant, "Retrait par gérant " + player.getName(), player.getName()));
            logic.saveData();
            player.sendMessage(ChatColor.GREEN + String.format("%,.2f€", montant) + " retirés de '" + nomEntreprise + "'. Solde: " + String.format("%,.2f€", entreprise.getSolde()) + ".");
        } else {
            player.sendMessage(ChatColor.RED + "Erreur dépôt compte: " + response.errorMessage);
        }
    }
}