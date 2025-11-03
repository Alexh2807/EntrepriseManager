
package com.gravityyfh.roleplaycity;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Représente une instance de contrat entre une entreprise et un joueur.
 */
public class Contrat {

    public enum ContratStatus {
        PROPOSE,
        ACCEPTE,
        ANNULE,
        TERMINE
    }

    private final UUID id;
    private final String serviceId;
    private final String nomEntrepriseFournisseur;
    private final UUID uuidInitiateur; // Gérant ou employé
    private final UUID uuidClient;
    private final double prixFinal;
    private ContratStatus status;
    private final LocalDateTime dateProposition;
    private LocalDateTime dateReponse; // Acceptation ou annulation

    public Contrat(String serviceId, String nomEntrepriseFournisseur, UUID uuidInitiateur, UUID uuidClient, double prixFinal) {
        this.id = UUID.randomUUID();
        this.serviceId = serviceId;
        this.nomEntrepriseFournisseur = nomEntrepriseFournisseur;
        this.uuidInitiateur = uuidInitiateur;
        this.uuidClient = uuidClient;
        this.prixFinal = prixFinal;
        this.status = ContratStatus.PROPOSE;
        this.dateProposition = LocalDateTime.now();
    }

    // Getters
    public UUID getId() { return id; }
    public String getServiceId() { return serviceId; }
    public String getNomEntrepriseFournisseur() { return nomEntrepriseFournisseur; }
    public UUID getUuidInitiateur() { return uuidInitiateur; }
    public UUID getUuidClient() { return uuidClient; }
    public double getPrixFinal() { return prixFinal; }
    public ContratStatus getStatus() { return status; }
    public LocalDateTime getDateProposition() { return dateProposition; }
    public LocalDateTime getDateReponse() { return dateReponse; }

    // Setters
    public void setStatus(ContratStatus status) {
        this.status = status;
        if (status == ContratStatus.ACCEPTE || status == ContratStatus.ANNULE) {
            this.dateReponse = LocalDateTime.now();
        }
    }
}