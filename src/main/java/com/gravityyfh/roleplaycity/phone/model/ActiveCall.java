package com.gravityyfh.roleplaycity.phone.model;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Represente un appel en cours entre deux joueurs
 */
public class ActiveCall {

    public enum CallState {
        RINGING,    // En attente de reponse
        CONNECTED,  // Appel en cours
        ENDING      // En cours de terminaison
    }

    private final UUID callId;
    private final UUID callerUuid;
    private final UUID calleeUuid;
    private final String callerNumber;
    private final String calleeNumber;
    private long startTime;
    private long connectedTime;
    private CallState state;
    private int creditsUsed;
    private int taskId; // ID de la tache Bukkit pour la facturation

    public ActiveCall(Player caller, Player callee, String callerNumber, String calleeNumber) {
        this.callId = UUID.randomUUID();
        this.callerUuid = caller.getUniqueId();
        this.calleeUuid = callee.getUniqueId();
        this.callerNumber = callerNumber;
        this.calleeNumber = calleeNumber;
        this.startTime = System.currentTimeMillis();
        this.state = CallState.RINGING;
        this.creditsUsed = 0;
        this.taskId = -1;
    }

    public ActiveCall(UUID callerUuid, UUID calleeUuid, String callerNumber, String calleeNumber) {
        this.callId = UUID.randomUUID();
        this.callerUuid = callerUuid;
        this.calleeUuid = calleeUuid;
        this.callerNumber = callerNumber;
        this.calleeNumber = calleeNumber;
        this.startTime = System.currentTimeMillis();
        this.state = CallState.RINGING;
        this.creditsUsed = 0;
        this.taskId = -1;
    }

    /**
     * Passe l'appel en etat connecte
     */
    public void connect() {
        this.state = CallState.CONNECTED;
        this.connectedTime = System.currentTimeMillis();
    }

    /**
     * Calcule la duree de l'appel en secondes
     */
    public int getDurationSeconds() {
        if (connectedTime == 0) return 0;
        return (int) ((System.currentTimeMillis() - connectedTime) / 1000);
    }

    /**
     * Formate la duree en MM:SS
     */
    public String getFormattedDuration() {
        int duration = getDurationSeconds();
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * Verifie si un joueur est implique dans cet appel
     */
    public boolean involves(UUID playerUuid) {
        return callerUuid.equals(playerUuid) || calleeUuid.equals(playerUuid);
    }

    /**
     * Retourne l'autre participant de l'appel
     */
    public UUID getOtherParticipant(UUID playerUuid) {
        if (callerUuid.equals(playerUuid)) {
            return calleeUuid;
        } else if (calleeUuid.equals(playerUuid)) {
            return callerUuid;
        }
        return null;
    }

    // Getters et Setters

    public UUID getCallId() {
        return callId;
    }

    public UUID getCallerUuid() {
        return callerUuid;
    }

    public UUID getCalleeUuid() {
        return calleeUuid;
    }

    public String getCallerNumber() {
        return callerNumber;
    }

    public String getCalleeNumber() {
        return calleeNumber;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getConnectedTime() {
        return connectedTime;
    }

    public CallState getState() {
        return state;
    }

    public void setState(CallState state) {
        this.state = state;
    }

    public int getCreditsUsed() {
        return creditsUsed;
    }

    public void addCreditsUsed(int credits) {
        this.creditsUsed += credits;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public boolean isCaller(UUID playerUuid) {
        return callerUuid.equals(playerUuid);
    }

    public void setBillingTaskId(int taskId) {
        this.taskId = taskId;
    }

    public void cancelBillingTask() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
