package com.gravityyfh.roleplaycity.contract.model;

/**
 * Statuts possibles d'un contrat
 */
public enum ContractStatus {
    /**
     * Contrat proposé, en attente d'acceptation
     */
    PROPOSE,

    /**
     * Contrat accepté, en cours d'exécution
     */
    ACCEPTE,

    /**
     * Contrat terminé avec succès
     */
    TERMINE,

    /**
     * Contrat en litige
     */
    LITIGE,

    /**
     * Litige résolu
     */
    RESOLU,

    /**
     * Contrat expiré sans réponse
     */
    EXPIRE,

    /**
     * Contrat rejeté par le client
     */
    REJETE;

    /**
     * @return true si le contrat est dans un état actif (non terminé)
     */
    public boolean isActive() {
        return this == PROPOSE || this == ACCEPTE || this == LITIGE;
    }

    /**
     * @return true si le contrat est dans l'historique (terminé)
     */
    public boolean isHistorical() {
        return this == TERMINE || this == RESOLU || this == EXPIRE || this == REJETE;
    }
}
