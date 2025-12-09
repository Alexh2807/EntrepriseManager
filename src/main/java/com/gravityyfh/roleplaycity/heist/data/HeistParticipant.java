package com.gravityyfh.roleplaycity.heist.data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Représente un participant à un cambriolage (cambrioleur)
 */
public class HeistParticipant {

    private final UUID playerUuid;
    private final String playerName;
    private final LocalDateTime joinTime;
    private final boolean isInitiator; // true si c'est celui qui a posé la bombe

    private ParticipantStatus status;
    private LocalDateTime disconnectTime; // null si connecté
    private int itemsStolen;
    private int chestsOpened;

    public HeistParticipant(UUID playerUuid, String playerName, boolean isInitiator) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.joinTime = LocalDateTime.now();
        this.isInitiator = isInitiator;
        this.status = ParticipantStatus.ACTIVE;
        this.disconnectTime = null;
        this.itemsStolen = 0;
        this.chestsOpened = 0;
    }

    /**
     * Marque le joueur comme déconnecté
     */
    public void setDisconnected() {
        this.status = ParticipantStatus.DISCONNECTED;
        this.disconnectTime = LocalDateTime.now();
    }

    /**
     * Marque le joueur comme reconnecté
     */
    public void setReconnected() {
        this.status = ParticipantStatus.ACTIVE;
        this.disconnectTime = null;
    }

    /**
     * Vérifie si le délai de grâce de déconnexion est expiré
     * @param graceMinutes minutes de grâce accordées
     * @return true si expiré
     */
    public boolean isDisconnectGraceExpired(int graceMinutes) {
        if (disconnectTime == null) return false;
        return LocalDateTime.now().isAfter(disconnectTime.plusMinutes(graceMinutes));
    }

    /**
     * Marque le joueur comme arrêté
     */
    public void setArrested() {
        this.status = ParticipantStatus.ARRESTED;
    }

    /**
     * Marque le joueur comme mort
     */
    public void setDead() {
        this.status = ParticipantStatus.DEAD;
    }

    /**
     * Marque le joueur comme ayant fui
     */
    public void setEscaped() {
        this.status = ParticipantStatus.ESCAPED;
    }

    /**
     * Ajoute des items volés au compteur
     */
    public void addStolenItems(int count) {
        this.itemsStolen += count;
    }

    /**
     * Incrémente le compteur de coffres ouverts
     */
    public void incrementChestsOpened() {
        this.chestsOpened++;
    }

    /**
     * @return true si le participant peut encore agir (voler, etc.)
     */
    public boolean canAct() {
        return status == ParticipantStatus.ACTIVE;
    }

    /**
     * @return true si le participant est hors-jeu (arrêté, mort, déconnecté trop longtemps)
     */
    public boolean isOutOfGame() {
        return status == ParticipantStatus.ARRESTED
            || status == ParticipantStatus.DEAD
            || status == ParticipantStatus.GRACE_EXPIRED;
    }

    // Getters

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public LocalDateTime getJoinTime() {
        return joinTime;
    }

    public boolean isInitiator() {
        return isInitiator;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public void setStatus(ParticipantStatus status) {
        this.status = status;
    }

    public LocalDateTime getDisconnectTime() {
        return disconnectTime;
    }

    public int getItemsStolen() {
        return itemsStolen;
    }

    public int getChestsOpened() {
        return chestsOpened;
    }

    /**
     * Statuts possibles d'un participant
     */
    public enum ParticipantStatus {
        ACTIVE,          // En jeu, peut agir
        DISCONNECTED,    // Déconnecté, dans la période de grâce
        GRACE_EXPIRED,   // Période de grâce expirée
        ARRESTED,        // Arrêté par la police
        DEAD,            // Mort pendant le cambriolage
        ESCAPED          // A fui la zone (quitté le terrain)
    }
}
