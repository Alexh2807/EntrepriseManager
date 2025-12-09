package com.gravityyfh.roleplaycity.entreprise.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.entreprise.model.Entreprise;
import com.gravityyfh.roleplaycity.entreprise.model.TransactionType;
import com.gravityyfh.roleplaycity.entreprise.persistence.EntrepriseRepository;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service de gestion de la paie (salaires et primes).
 *
 * Responsabilités:
 * - Gestion des primes individuelles
 * - Calcul et paiement des salaires
 * - Historique des paiements
 */
public class PayrollService {

    private final RoleplayCity plugin;
    private final EntrepriseRepository repository;
    private final EconomyService economyService;

    public PayrollService(RoleplayCity plugin, EntrepriseRepository repository, EconomyService economyService) {
        this.plugin = plugin;
        this.repository = repository;
        this.economyService = economyService;
    }

    /**
     * Définit la prime d'un employé.
     *
     * @param entreprise L'entreprise
     * @param employeeUuid UUID de l'employé
     * @param prime Montant de la prime
     * @return true si succès
     */
    public boolean setPrime(Entreprise entreprise, UUID employeeUuid, double prime) {
        if (entreprise == null || employeeUuid == null || prime < 0) {
            return false;
        }

        entreprise.setPrimePourEmploye(employeeUuid.toString(), prime);
        return repository.save(entreprise);
    }

    /**
     * Récupère la prime d'un employé.
     *
     * @param entreprise L'entreprise
     * @param employeeUuid UUID de l'employé
     * @return Montant de la prime
     */
    public double getPrime(Entreprise entreprise, UUID employeeUuid) {
        if (entreprise == null || employeeUuid == null) {
            return 0.0;
        }
        return entreprise.getPrimePourEmploye(employeeUuid.toString());
    }

    /**
     * Retire la prime d'un employé.
     *
     * @param entreprise L'entreprise
     * @param employeeUuid UUID de l'employé
     * @return true si succès
     */
    public boolean removePrime(Entreprise entreprise, UUID employeeUuid) {
        if (entreprise == null || employeeUuid == null) {
            return false;
        }

        entreprise.retirerPrimeEmploye(employeeUuid.toString());
        return repository.save(entreprise);
    }

    /**
     * Récupère toutes les primes.
     *
     * @param entreprise L'entreprise
     * @return Map UUID → Prime
     */
    public Map<String, Double> getAllPrimes(Entreprise entreprise) {
        if (entreprise == null) {
            return new HashMap<>();
        }
        return entreprise.getPrimes();
    }

    /**
     * Calcule le total des primes.
     *
     * @param entreprise L'entreprise
     * @return Total des primes
     */
    public double calculateTotalPrimes(Entreprise entreprise) {
        if (entreprise == null) {
            return 0.0;
        }

        return entreprise.getPrimes().values().stream()
            .mapToDouble(Double::doubleValue)
            .sum();
    }

    /**
     * Paie le salaire d'un employé spécifique.
     *
     * @param entreprise L'entreprise
     * @param employee Le joueur employé
     * @param salaireBase Salaire de base
     * @return true si succès
     */
    public boolean paySalary(Entreprise entreprise, Player employee, double salaireBase) {
        if (entreprise == null || employee == null || salaireBase < 0) {
            return false;
        }

        // Récupérer la prime
        double prime = entreprise.getPrimePourEmploye(employee.getUniqueId().toString());
        double montantTotal = salaireBase + prime;

        // Vérifier les fonds
        if (!economyService.hasSufficientFunds(entreprise, montantTotal)) {
            plugin.getLogger().warning("Fonds insuffisants pour payer " + employee.getName());
            return false;
        }

        // Effectuer le paiement via Vault
        if (RoleplayCity.getEconomy() != null) {
            RoleplayCity.getEconomy().depositPlayer(employee, montantTotal);
        }

        // Enregistrer la dépense
        String description = String.format("Salaire %s (base: %.2f€ + prime: %.2f€)",
            employee.getName(), salaireBase, prime);

        return economyService.addExpense(
            entreprise,
            montantTotal,
            TransactionType.SALARY,
            description,
            "System"
        );
    }

    /**
     * Paie tous les salaires de l'entreprise.
     *
     * @param entreprise L'entreprise
     * @param salaireBase Salaire de base par employé
     * @return Nombre d'employés payés
     */
    public int payAllSalaries(Entreprise entreprise, double salaireBase) {
        if (entreprise == null || salaireBase < 0) {
            return 0;
        }

        int nbPaies = 0;
        double montantTotal = 0.0;

        // Calculer le montant total nécessaire
        for (String employeName : entreprise.getEmployes()) {
            Player employee = plugin.getServer().getPlayerExact(employeName);
            if (employee != null) {
                double prime = entreprise.getPrimePourEmploye(employee.getUniqueId().toString());
                montantTotal += salaireBase + prime;
            }
        }

        // Vérifier les fonds
        if (!economyService.hasSufficientFunds(entreprise, montantTotal)) {
            plugin.getLogger().warning("Fonds insuffisants pour payer tous les salaires");
            return 0;
        }

        // Payer chaque employé
        for (String employeName : entreprise.getEmployes()) {
            Player employee = plugin.getServer().getPlayerExact(employeName);
            if (employee != null) {
                if (paySalary(entreprise, employee, salaireBase)) {
                    nbPaies++;
                }
            }
        }

        return nbPaies;
    }

    /**
     * Calcule le coût total de la paie (salaires + primes).
     *
     * @param entreprise L'entreprise
     * @param salaireBase Salaire de base par employé
     * @return Coût total
     */
    public double calculatePayrollCost(Entreprise entreprise, double salaireBase) {
        if (entreprise == null) {
            return 0.0;
        }

        int nbEmployes = entreprise.getEmployes().size();
        double coutTotal = nbEmployes * salaireBase;
        coutTotal += calculateTotalPrimes(entreprise);

        return coutTotal;
    }

    /**
     * Verse une prime exceptionnelle à un employé.
     *
     * @param entreprise L'entreprise
     * @param employee Le joueur
     * @param montant Montant de la prime
     * @param raison Raison de la prime
     * @return true si succès
     */
    public boolean giveBonus(Entreprise entreprise, Player employee, double montant, String raison) {
        if (entreprise == null || employee == null || montant <= 0) {
            return false;
        }

        // Vérifier les fonds
        if (!economyService.hasSufficientFunds(entreprise, montant)) {
            plugin.getLogger().warning("Fonds insuffisants pour prime exceptionnelle");
            return false;
        }

        // Effectuer le paiement via Vault
        if (RoleplayCity.getEconomy() != null) {
            RoleplayCity.getEconomy().depositPlayer(employee, montant);
        }

        // Enregistrer la dépense
        String description = String.format("Prime exceptionnelle %s: %s", employee.getName(), raison);

        return economyService.addExpense(
            entreprise,
            montant,
            TransactionType.BONUS,
            description,
            employee.getName()
        );
    }

    /**
     * Récupère le salaire de base configuré pour un type d'entreprise.
     *
     * @param type Le type d'entreprise
     * @return Salaire de base
     */
    public double getBaseSalary(String type) {
        return plugin.getConfig().getDouble("types-entreprise." + type + ".salaire-base", 100.0);
    }

    /**
     * Vérifie si l'entreprise peut payer les salaires.
     *
     * @param entreprise L'entreprise
     * @param salaireBase Salaire de base
     * @return true si fonds suffisants
     */
    public boolean canPaySalaries(Entreprise entreprise, double salaireBase) {
        if (entreprise == null) {
            return false;
        }

        double coutTotal = calculatePayrollCost(entreprise, salaireBase);
        return economyService.hasSufficientFunds(entreprise, coutTotal);
    }
}
