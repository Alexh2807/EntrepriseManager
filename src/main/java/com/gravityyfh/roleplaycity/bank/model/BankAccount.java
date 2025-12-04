package com.gravityyfh.roleplaycity.bank.model;

import java.util.UUID;

/**
 * Représente un compte bancaire personnel d'un joueur
 * Architecture identique à Town/Entreprise pour cohérence
 */
public class BankAccount {

    private final UUID playerUuid;
    private final String playerName;
    private double balance;           // Solde en banque
    private int accountLevel;         // Niveau du compte (détermine la limite)
    private final long createdAt;     // Date de création
    private long lastTransaction;     // Dernière transaction

    // Limites basées sur le niveau du compte
    private static final double[] LEVEL_LIMITS = {
        10000.0,    // Niveau 1 : 10k
        25000.0,    // Niveau 2 : 25k
        50000.0,    // Niveau 3 : 50k
        100000.0,   // Niveau 4 : 100k
        250000.0,   // Niveau 5 : 250k
        500000.0,   // Niveau 6 : 500k
        1000000.0,  // Niveau 7 : 1M
        2500000.0,  // Niveau 8 : 2.5M
        5000000.0,  // Niveau 9 : 5M
        Double.MAX_VALUE // Niveau 10 : Illimité
    };

    public BankAccount(UUID playerUuid, String playerName, double balance, int accountLevel, long createdAt, long lastTransaction) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.balance = balance;
        this.accountLevel = Math.max(1, Math.min(10, accountLevel)); // Entre 1 et 10
        this.createdAt = createdAt;
        this.lastTransaction = lastTransaction;
    }

    // Constructor pour nouveau compte
    public BankAccount(UUID playerUuid, String playerName) {
        this(playerUuid, playerName, 0.0, 1, System.currentTimeMillis(), System.currentTimeMillis());
    }

    // Getters
    public UUID getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public double getBalance() { return balance; }
    public int getAccountLevel() { return accountLevel; }
    public long getCreatedAt() { return createdAt; }
    public long getLastTransaction() { return lastTransaction; }

    /**
     * Retourne la limite de dépôt basée sur le niveau du compte
     */
    public double getDepositLimit() {
        if (accountLevel < 1 || accountLevel > LEVEL_LIMITS.length) {
            return LEVEL_LIMITS[0];
        }
        return LEVEL_LIMITS[accountLevel - 1];
    }

    /**
     * Retourne l'espace disponible avant d'atteindre la limite
     */
    public double getRemainingSpace() {
        double limit = getDepositLimit();
        if (limit == Double.MAX_VALUE) {
            return Double.MAX_VALUE;
        }
        return Math.max(0, limit - balance);
    }

    /**
     * Vérifie si un montant peut être déposé
     */
    public boolean canDeposit(double amount) {
        if (amount <= 0) return false;
        double limit = getDepositLimit();
        if (limit == Double.MAX_VALUE) return true;
        return (balance + amount) <= limit;
    }

    /**
     * Vérifie si un montant peut être retiré
     */
    public boolean canWithdraw(double amount) {
        return amount > 0 && balance >= amount;
    }

    // Setters
    public void setBalance(double balance) {
        this.balance = Math.max(0, balance);
        this.lastTransaction = System.currentTimeMillis();
    }

    public void setAccountLevel(int level) {
        this.accountLevel = Math.max(1, Math.min(10, level));
    }

    /**
     * Dépose de l'argent sur le compte
     * @return true si succès, false si échec (limite atteinte)
     */
    public boolean deposit(double amount) {
        if (!canDeposit(amount)) {
            return false;
        }
        this.balance += amount;
        this.lastTransaction = System.currentTimeMillis();
        return true;
    }

    /**
     * Retire de l'argent du compte
     * @return true si succès, false si fonds insuffisants
     */
    public boolean withdraw(double amount) {
        if (!canWithdraw(amount)) {
            return false;
        }
        this.balance -= amount;
        this.lastTransaction = System.currentTimeMillis();
        return true;
    }

    /**
     * Retourne le coût pour améliorer au niveau suivant
     */
    public double getUpgradeCost() {
        if (accountLevel >= 10) return 0;

        // Coût progressif : 5k, 10k, 25k, 50k, 100k, 250k, 500k, 1M, 2.5M
        double[] costs = {
            5000.0, 10000.0, 25000.0, 50000.0,
            100000.0, 250000.0, 500000.0,
            1000000.0, 2500000.0
        };

        return costs[accountLevel - 1];
    }

    /**
     * Améliore le compte au niveau suivant
     * @return true si succès
     */
    public boolean upgrade(double pocketMoney) {
        if (accountLevel >= 10) return false;

        double cost = getUpgradeCost();
        if (pocketMoney < cost) return false;

        accountLevel++;
        lastTransaction = System.currentTimeMillis();
        return true;
    }
}
