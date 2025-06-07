package com.gravityyfh.entreprisemanager.Models;

import org.bukkit.entity.Player;

public class DemandeCreation {
    public final Player maire;
    public final Player gerant;
    public final String type;
    public final String ville;
    public final String siret;
    public final String nomEntreprise;
    public final double cout;
    private final long expirationTimeMillis;

    public DemandeCreation(Player maire, Player gerant, String type, String ville, String siret, String nomEntreprise, double cout, long dureeValiditeMillis) {
        this.maire = maire;
        this.gerant = gerant;
        this.type = type;
        this.ville = ville;
        this.siret = siret;
        this.nomEntreprise = nomEntreprise;
        this.cout = cout;
        this.expirationTimeMillis = System.currentTimeMillis() + dureeValiditeMillis;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expirationTimeMillis;
    }

    public long getExpirationTimeMillis() {
        return expirationTimeMillis;
    }
}