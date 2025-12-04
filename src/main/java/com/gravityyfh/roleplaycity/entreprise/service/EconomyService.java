package com.gravityyfh.roleplaycity.entreprise.service;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.entreprise.model.Entreprise;
import com.gravityyfh.roleplaycity.entreprise.model.Transaction;
import com.gravityyfh.roleplaycity.entreprise.model.TransactionType;
import com.gravityyfh.roleplaycity.entreprise.persistence.EntrepriseRepository;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Level;

/**
 * Service de gestion de l'économie des entreprises.
 *
 * Responsabilités:
 * - Gestion du solde
 * - Transactions financières (dépôts, retraits, revenus, dépenses)
 * - Historique des transactions
 * - Calculs financiers (profit/perte, chiffre d'affaires)
 */
public class EconomyService {

    private final RoleplayCity plugin;
    private final EntrepriseRepository repository;

    public EconomyService(RoleplayCity plugin, EntrepriseRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    /**
     * Dépose de l'argent dans une entreprise.
     *
     * @param entreprise L'entreprise
     * @param montant Le montant à déposer
     * @param initiateur Le joueur effectuant le dépôt
     * @param description Description optionnelle
     * @return true si succès
     */
    public boolean deposit(Entreprise entreprise, double montant, Player initiateur, String description) {
        if (entreprise == null || montant <= 0) {
            return false;
        }

        // Vérifier la limite de solde
        double nouveauSolde = entreprise.getSolde() + montant;
        double maxSolde = getMaxSoldePourNiveau(entreprise.getNiveauMaxSolde());

        if (nouveauSolde > maxSolde) {
            plugin.getLogger().warning("Dépôt refusé: dépasserait la limite de solde (" + maxSolde + "€)");
            return false;
        }

        // Mettre à jour le solde
        entreprise.setSolde(nouveauSolde);

        // Enregistrer la transaction
        Transaction transaction = new Transaction(
            TransactionType.DEPOSIT,
            montant,
            description != null ? description : "Dépôt",
            initiateur.getName()
        );
        entreprise.addTransaction(transaction);

        // Sauvegarder
        return repository.save(entreprise);
    }

    /**
     * Retire de l'argent d'une entreprise.
     *
     * @param entreprise L'entreprise
     * @param montant Le montant à retirer
     * @param initiateur Le joueur effectuant le retrait
     * @param description Description optionnelle
     * @return true si succès
     */
    public boolean withdraw(Entreprise entreprise, double montant, Player initiateur, String description) {
        if (entreprise == null || montant <= 0) {
            return false;
        }

        // Vérifier les fonds disponibles
        if (entreprise.getSolde() < montant) {
            plugin.getLogger().warning("Retrait refusé: fonds insuffisants");
            return false;
        }

        // Mettre à jour le solde
        entreprise.setSolde(entreprise.getSolde() - montant);

        // Enregistrer la transaction
        Transaction transaction = new Transaction(
            TransactionType.WITHDRAWAL,
            montant,
            description != null ? description : "Retrait",
            initiateur.getName()
        );
        entreprise.addTransaction(transaction);

        // Sauvegarder
        return repository.save(entreprise);
    }

    /**
     * Enregistre un revenu pour l'entreprise.
     *
     * @param entreprise L'entreprise
     * @param montant Le montant du revenu
     * @param type Le type de revenu (REVENUE, SALE, etc.)
     * @param description Description
     * @param initiateur Qui a généré le revenu
     * @return true si succès
     */
    public boolean addRevenue(
        Entreprise entreprise,
        double montant,
        TransactionType type,
        String description,
        String initiateur
    ) {
        if (entreprise == null || montant <= 0) {
            return false;
        }

        if (!type.isOperationalIncome()) {
            plugin.getLogger().warning("Type de transaction invalide pour revenu: " + type);
            return false;
        }

        // Vérifier la limite de solde
        double nouveauSolde = entreprise.getSolde() + montant;
        double maxSolde = getMaxSoldePourNiveau(entreprise.getNiveauMaxSolde());

        if (nouveauSolde > maxSolde) {
            // Plafonner au maximum
            montant = maxSolde - entreprise.getSolde();
            nouveauSolde = maxSolde;
        }

        // Mettre à jour le solde et CA
        entreprise.setSolde(nouveauSolde);
        entreprise.setChiffreAffairesTotal(entreprise.getChiffreAffairesTotal() + montant);

        // Enregistrer la transaction
        Transaction transaction = new Transaction(type, montant, description, initiateur);
        entreprise.addTransaction(transaction);

        // Sauvegarder
        return repository.save(entreprise);
    }

    /**
     * Enregistre une dépense pour l'entreprise.
     *
     * @param entreprise L'entreprise
     * @param montant Le montant de la dépense
     * @param type Le type de dépense (SALARY, EXPENSE, etc.)
     * @param description Description
     * @param initiateur Qui a effectué la dépense
     * @return true si succès
     */
    public boolean addExpense(
        Entreprise entreprise,
        double montant,
        TransactionType type,
        String description,
        String initiateur
    ) {
        if (entreprise == null || montant <= 0) {
            return false;
        }

        if (!type.isOperationalExpense() && type != TransactionType.WITHDRAWAL) {
            plugin.getLogger().warning("Type de transaction invalide pour dépense: " + type);
            return false;
        }

        // Vérifier les fonds disponibles
        if (entreprise.getSolde() < montant) {
            plugin.getLogger().warning("Dépense refusée: fonds insuffisants");
            return false;
        }

        // Mettre à jour le solde
        entreprise.setSolde(entreprise.getSolde() - montant);

        // Enregistrer la transaction
        Transaction transaction = new Transaction(type, montant, description, initiateur);
        entreprise.addTransaction(transaction);

        // Sauvegarder
        return repository.save(entreprise);
    }

    /**
     * Transfère de l'argent entre deux entreprises.
     *
     * @param source Entreprise source
     * @param destination Entreprise destination
     * @param montant Montant à transférer
     * @param initiateur Joueur initiant le transfert
     * @param description Description du transfert
     * @return true si succès
     */
    public boolean transfer(
        Entreprise source,
        Entreprise destination,
        double montant,
        Player initiateur,
        String description
    ) {
        if (source == null || destination == null || montant <= 0) {
            return false;
        }

        // Vérifier les fonds disponibles
        if (source.getSolde() < montant) {
            plugin.getLogger().warning("Transfert refusé: fonds insuffisants");
            return false;
        }

        // Vérifier la limite de solde destination
        double nouveauSoldeDestination = destination.getSolde() + montant;
        double maxSolde = getMaxSoldePourNiveau(destination.getNiveauMaxSolde());

        if (nouveauSoldeDestination > maxSolde) {
            plugin.getLogger().warning("Transfert refusé: dépasserait la limite de solde destination");
            return false;
        }

        // Effectuer le transfert
        source.setSolde(source.getSolde() - montant);
        destination.setSolde(nouveauSoldeDestination);

        // Enregistrer les transactions
        String descSource = description != null ? description : "Transfert vers " + destination.getNom();
        String descDest = description != null ? description : "Transfert depuis " + source.getNom();

        Transaction txSource = new Transaction(
            TransactionType.TRANSFER_OUT,
            montant,
            descSource,
            initiateur.getName()
        );
        Transaction txDest = new Transaction(
            TransactionType.TRANSFER_IN,
            montant,
            descDest,
            initiateur.getName()
        );

        source.addTransaction(txSource);
        destination.addTransaction(txDest);

        // Sauvegarder les deux entreprises
        boolean savedSource = repository.save(source);
        boolean savedDest = repository.save(destination);

        return savedSource && savedDest;
    }

    /**
     * Paie les taxes d'une entreprise.
     *
     * @param entreprise L'entreprise
     * @param montant Montant des taxes
     * @return true si succès
     */
    public boolean payTaxes(Entreprise entreprise, double montant) {
        return addExpense(
            entreprise,
            montant,
            TransactionType.TAXES,
            "Taxes périodiques",
            "System"
        );
    }

    /**
     * Paie les salaires de tous les employés.
     *
     * @param entreprise L'entreprise
     * @param salaireBase Salaire de base par employé
     * @return true si succès
     */
    public boolean payAllSalaries(Entreprise entreprise, double salaireBase) {
        if (entreprise == null || salaireBase <= 0) {
            return false;
        }

        int nbEmployes = entreprise.getEmployes().size();
        double totalSalaires = salaireBase * nbEmployes;

        // Ajouter les primes
        for (Double prime : entreprise.getPrimes().values()) {
            totalSalaires += prime;
        }

        // Vérifier les fonds disponibles
        if (entreprise.getSolde() < totalSalaires) {
            plugin.getLogger().warning("Paiement salaires refusé: fonds insuffisants");
            return false;
        }

        return addExpense(
            entreprise,
            totalSalaires,
            TransactionType.SALARY,
            "Salaires périodiques (" + nbEmployes + " employés)",
            "System"
        );
    }

    /**
     * Calcule le profit ou la perte sur une période.
     *
     * @param entreprise L'entreprise
     * @param start Début de la période
     * @param end Fin de la période
     * @return Profit (positif) ou perte (négatif)
     */
    public double calculateProfitLoss(Entreprise entreprise, LocalDateTime start, LocalDateTime end) {
        if (entreprise == null) {
            return 0.0;
        }
        return entreprise.calculateProfitLoss(start, end);
    }

    /**
     * Récupère l'historique des transactions.
     *
     * @param entreprise L'entreprise
     * @return Liste des transactions (plus récentes en premier)
     */
    public List<Transaction> getTransactionHistory(Entreprise entreprise) {
        if (entreprise == null) {
            return List.of();
        }
        return entreprise.getTransactionLog();
    }

    /**
     * Récupère les N dernières transactions.
     *
     * @param entreprise L'entreprise
     * @param limit Nombre maximum de transactions
     * @return Liste des transactions
     */
    public List<Transaction> getRecentTransactions(Entreprise entreprise, int limit) {
        if (entreprise == null) {
            return List.of();
        }

        List<Transaction> all = entreprise.getTransactionLog();
        return all.stream()
            .sorted((a, b) -> b.timestamp.compareTo(a.timestamp))
            .limit(limit)
            .toList();
    }

    /**
     * Récupère les transactions d'un type spécifique.
     *
     * @param entreprise L'entreprise
     * @param type Le type de transaction
     * @return Liste des transactions
     */
    public List<Transaction> getTransactionsByType(Entreprise entreprise, TransactionType type) {
        if (entreprise == null) {
            return List.of();
        }

        return entreprise.getTransactionLog().stream()
            .filter(tx -> tx.type == type)
            .toList();
    }

    /**
     * Calcule le total des revenus sur une période.
     *
     * @param entreprise L'entreprise
     * @param start Début de la période
     * @param end Fin de la période
     * @return Total des revenus
     */
    public double calculateTotalRevenue(Entreprise entreprise, LocalDateTime start, LocalDateTime end) {
        if (entreprise == null) {
            return 0.0;
        }

        return entreprise.getTransactionLog().stream()
            .filter(tx -> !tx.timestamp.isBefore(start) && tx.timestamp.isBefore(end))
            .filter(tx -> tx.type.isOperationalIncome())
            .mapToDouble(tx -> tx.amount)
            .sum();
    }

    /**
     * Calcule le total des dépenses sur une période.
     *
     * @param entreprise L'entreprise
     * @param start Début de la période
     * @param end Fin de la période
     * @return Total des dépenses
     */
    public double calculateTotalExpenses(Entreprise entreprise, LocalDateTime start, LocalDateTime end) {
        if (entreprise == null) {
            return 0.0;
        }

        return entreprise.getTransactionLog().stream()
            .filter(tx -> !tx.timestamp.isBefore(start) && tx.timestamp.isBefore(end))
            .filter(tx -> tx.type.isOperationalExpense())
            .mapToDouble(tx -> Math.abs(tx.amount))
            .sum();
    }

    /**
     * Vérifie si une entreprise a suffisamment de fonds.
     *
     * @param entreprise L'entreprise
     * @param montant Le montant requis
     * @return true si fonds suffisants
     */
    public boolean hasSufficientFunds(Entreprise entreprise, double montant) {
        return entreprise != null && entreprise.getSolde() >= montant;
    }

    /**
     * Calcule le solde maximum selon le niveau.
     */
    private double getMaxSoldePourNiveau(int niveau) {
        return plugin.getConfig().getDouble("entreprise.niveaux.solde.niveau-" + niveau + ".max", 100000.0);
    }

    /**
     * Récupère le solde actuel.
     */
    public double getSolde(Entreprise entreprise) {
        return entreprise != null ? entreprise.getSolde() : 0.0;
    }

    /**
     * Récupère le chiffre d'affaires total.
     */
    public double getChiffreAffaires(Entreprise entreprise) {
        return entreprise != null ? entreprise.getChiffreAffairesTotal() : 0.0;
    }

    /**
     * Définit le solde (pour administration).
     */
    public boolean setSolde(Entreprise entreprise, double nouveauSolde) {
        if (entreprise == null || nouveauSolde < 0) {
            return false;
        }

        entreprise.setSolde(nouveauSolde);
        return repository.save(entreprise);
    }
}
