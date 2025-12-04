package com.gravityyfh.roleplaycity.entreprise.model;

import org.bukkit.entity.Player;

/**
 * Demande de création d'entreprise en attente de validation par le maire.
 * Contient toutes les informations nécessaires et un timestamp d'expiration.
 */
public record DemandeCreation(
    Player maire,
    Player gerant,
    String type,
    String ville,
    String siret,
    String nomEntreprise,
    double cout,
    long expirationTimeMillis
) {
    /**
     * Constructeur qui calcule automatiquement le timestamp d'expiration.
     * @param expirationTimeMillis Durée de validité en millisecondes (ex: 300000 = 5 minutes)
     */
    public DemandeCreation(
        Player maire,
        Player gerant,
        String type,
        String ville,
        String siret,
        String nomEntreprise,
        double cout,
        long expirationTimeMillis
    ) {
        this.maire = maire;
        this.gerant = gerant;
        this.type = type;
        this.ville = ville;
        this.siret = siret;
        this.nomEntreprise = nomEntreprise;
        this.cout = cout;
        this.expirationTimeMillis = System.currentTimeMillis() + expirationTimeMillis;
    }

    /**
     * Vérifie si la demande a expiré.
     * @return true si le temps d'expiration est dépassé
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTimeMillis;
    }
}
