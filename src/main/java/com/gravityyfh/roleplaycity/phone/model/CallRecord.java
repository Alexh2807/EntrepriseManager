package com.gravityyfh.roleplaycity.phone.model;

/**
 * Represente un enregistrement d'appel dans l'historique
 */
public class CallRecord {

    public enum CallStatus {
        COMPLETED,  // Appel termine normalement
        MISSED,     // Appel manque (non repondu)
        REJECTED,   // Appel rejete
        FAILED      // Echec (credits insuffisants, etc.)
    }

    private int id;
    private String callerNumber;
    private String calleeNumber;
    private long startedAt;
    private Long endedAt;
    private int durationSeconds;
    private CallStatus status;
    private int costCredits;

    public CallRecord() {
        this.status = CallStatus.COMPLETED;
    }

    public CallRecord(String callerNumber, String calleeNumber) {
        this.callerNumber = callerNumber;
        this.calleeNumber = calleeNumber;
        this.startedAt = System.currentTimeMillis();
        this.status = CallStatus.COMPLETED;
        this.durationSeconds = 0;
        this.costCredits = 0;
    }

    public CallRecord(int id, String callerNumber, String calleeNumber, long startedAt, Long durationSeconds, CallStatus status) {
        this.id = id;
        this.callerNumber = callerNumber;
        this.calleeNumber = calleeNumber;
        this.startedAt = startedAt;
        this.durationSeconds = durationSeconds != null ? durationSeconds.intValue() : 0;
        this.status = status;
    }

    /**
     * Termine l'appel et calcule la duree
     */
    public void endCall() {
        this.endedAt = System.currentTimeMillis();
        this.durationSeconds = (int) ((endedAt - startedAt) / 1000);
    }

    /**
     * Formate la duree en MM:SS
     */
    public String getFormattedDuration() {
        int minutes = durationSeconds / 60;
        int seconds = durationSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // Getters et Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getCallerNumber() {
        return callerNumber;
    }

    public void setCallerNumber(String callerNumber) {
        this.callerNumber = callerNumber;
    }

    public String getCalleeNumber() {
        return calleeNumber;
    }

    public void setCalleeNumber(String calleeNumber) {
        this.calleeNumber = calleeNumber;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Long endedAt) {
        this.endedAt = endedAt;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public CallStatus getStatus() {
        return status;
    }

    public void setStatus(CallStatus status) {
        this.status = status;
    }

    public int getCostCredits() {
        return costCredits;
    }

    public void setCostCredits(int costCredits) {
        this.costCredits = costCredits;
    }
}
