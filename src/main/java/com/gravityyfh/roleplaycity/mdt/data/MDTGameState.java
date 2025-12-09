package com.gravityyfh.roleplaycity.mdt.data;

/**
 * États possibles d'une partie MDT Rush
 */
public enum MDTGameState {
    /**
     * Aucune partie en cours
     */
    INACTIVE,

    /**
     * Phase de recrutement - joueurs peuvent rejoindre (60s apres /mdt start)
     */
    JOINING,

    /**
     * Lobby ouvert, joueurs attendent le debut de la partie (60s countdown)
     */
    LOBBY,

    /**
     * Compte à rebours avant le début de la partie
     */
    COUNTDOWN,

    /**
     * Partie en cours
     */
    PLAYING,

    /**
     * Partie terminée, en cours de nettoyage
     */
    ENDED
}
