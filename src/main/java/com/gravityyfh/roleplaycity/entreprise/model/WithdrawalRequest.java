package com.gravityyfh.roleplaycity.entreprise.model;

/**
 * Demande de retrait d'argent en attente de confirmation.
 * Expire après 30 secondes si non confirmée.
 */
public class WithdrawalRequest {
    private static final long CONFIRMATION_TIMEOUT_MS = 30000; // 30 secondes

    public final String entrepriseName;
    public final double amount;
    public final long timestamp;

    public WithdrawalRequest(String entrepriseName, double amount) {
        this.entrepriseName = entrepriseName;
        this.amount = amount;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Vérifie si la demande a expiré (plus de 30 secondes).
     * @return true si expirée
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > CONFIRMATION_TIMEOUT_MS;
    }
}
