package com.gravityyfh.entreprisemanager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Représente un service prédéfini dans la configuration.
 */
public class Service {
    private final String id; // ex: "Construction_Maison_Simple"
    private final String nomAffiche;
    private final List<String> description;
    private final double prix;

    public Service(String id, String nomAffiche, List<String> description, double prix) {
        this.id = id;
        this.nomAffiche = nomAffiche;
        this.description = description;
        this.prix = prix;
    }

    public String getId() {
        return id;
    }

    public String getNomAffiche() {
        return nomAffiche;
    }

    public List<String> getDescription() {
        return description;
    }

    public double getPrix() {
        return prix;
    }
}

