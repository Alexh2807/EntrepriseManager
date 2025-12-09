package com.gravityyfh.roleplaycity.entreprise.model;

/**
 * Type de transaction financière d'une entreprise.
 * Détermine si la transaction est un revenu ou une dépense.
 */
public enum TransactionType {
    DEPOSIT("Dépôt de Capital"),
    WITHDRAWAL("Retrait de Capital"),
    REVENUE("Revenu d'Activité"),
    TAXES("Impôts sur Revenu"),
    PRIMES("Paiement des Primes"),
    SALARY("Paiement de Salaire"),
    BONUS("Prime Exceptionnelle"),
    EXPENSE("Dépense"),
    OTHER_EXPENSE("Autre Dépense Op."),
    OTHER_INCOME("Autre Revenu Op."),
    CREATION_COST("Frais de Création"),
    RENAME_COST("Frais de Renommage"),
    PAYROLL_TAX("Charges Salariales"),
    TRANSFER_IN("Transfert Reçu"),
    TRANSFER_OUT("Transfert Envoyé");

    private final String displayName;

    TransactionType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Vérifie si cette transaction est un revenu opérationnel.
     * 
     * @return true si REVENUE, OTHER_INCOME ou TRANSFER_IN
     */
    public boolean isOperationalIncome() {
        return this == REVENUE || this == OTHER_INCOME || this == TRANSFER_IN;
    }

    /**
     * Vérifie si cette transaction est une dépense opérationnelle.
     * 
     * @return true si c'est une dépense (taxes, salaires, primes, frais, etc.)
     */
    public boolean isOperationalExpense() {
        return this == TAXES
                || this == PRIMES
                || this == SALARY
                || this == BONUS
                || this == OTHER_EXPENSE
                || this == CREATION_COST
                || this == RENAME_COST
                || this == PAYROLL_TAX
                || this == TRANSFER_OUT;
    }
}
