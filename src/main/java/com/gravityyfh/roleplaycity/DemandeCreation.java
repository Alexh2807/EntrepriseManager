package com.gravityyfh.roleplaycity;

import org.bukkit.entity.Player;

public record DemandeCreation(Player maire, Player gerant, String type, String ville, String siret,
                              String nomEntreprise, double cout, long expiration) {
    public DemandeCreation(Player maire, Player gerant, String type, String ville, String siret, String nomEntreprise, double cout, long expiration) {
        this.maire = maire;
        this.gerant = gerant;
        this.type = type;
        this.ville = ville;
        this.siret = siret;
        this.nomEntreprise = nomEntreprise;
        this.cout = cout;
        this.expiration = System.currentTimeMillis() + expiration;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiration;
    }
}
