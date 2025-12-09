package com.gravityyfh.roleplaycity.entreprise.model;

import java.util.List;

/**
 * Informations sur une restriction pour un type d'entreprise.
 * Utilisé dans le cache de restrictions pour optimiser les performances.
 */
public record RestrictionInfo(
    String entrepriseType,
    int limiteNonMembre,
    List<String> messagesErreur
) {
}
