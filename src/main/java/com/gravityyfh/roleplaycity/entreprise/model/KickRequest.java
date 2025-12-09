package com.gravityyfh.roleplaycity.entreprise.model;

/**
 * Demande de licenciement d'un employé en attente de confirmation.
 * Expire après 30 secondes si non confirmée.
 */
public class KickRequest {
    private static final long CONFIRMATION_TIMEOUT_MS = 30000; // 30 secondes

    public final String entrepriseName;
    public final String employeeName;
    public final long timestamp;

    public KickRequest(String entrepriseName, String employeeName) {
        this.entrepriseName = entrepriseName;
        this.employeeName = employeeName;
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
