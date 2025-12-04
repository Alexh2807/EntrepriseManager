package com.gravityyfh.roleplaycity.phone.model;

import java.util.*;

/**
 * Represente le compte telephone d'un joueur (donnees Cloud)
 */
public class PhoneAccount {

    private int id;
    private UUID ownerUuid;
    private String ownerName;
    private String phoneNumber;
    private long createdAt;
    private Long updatedAt;

    // Statistiques
    private int totalCalls = 0;
    private int totalSms = 0;

    // Parametres
    private boolean silentMode = false;
    private Set<String> blockedNumbers = new HashSet<>();

    public PhoneAccount() {
    }

    public PhoneAccount(UUID ownerUuid, String ownerName, String phoneNumber) {
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.phoneNumber = phoneNumber;
        this.createdAt = System.currentTimeMillis();
    }

    public PhoneAccount(int id, UUID ownerUuid, String ownerName, String phoneNumber, long createdAt, Long updatedAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.phoneNumber = phoneNumber;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters et Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Met a jour le timestamp de derniere modification
     */
    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    // Getters et Setters pour les statistiques

    public int getTotalCalls() {
        return totalCalls;
    }

    public void setTotalCalls(int totalCalls) {
        this.totalCalls = totalCalls;
    }

    public void incrementTotalCalls() {
        this.totalCalls++;
    }

    public int getTotalSms() {
        return totalSms;
    }

    public void setTotalSms(int totalSms) {
        this.totalSms = totalSms;
    }

    public void incrementTotalSms() {
        this.totalSms++;
    }

    // Getters et Setters pour les parametres

    public boolean isSilentMode() {
        return silentMode;
    }

    public void setSilentMode(boolean silentMode) {
        this.silentMode = silentMode;
    }

    public void toggleSilentMode() {
        this.silentMode = !this.silentMode;
    }

    public Set<String> getBlockedNumbers() {
        return blockedNumbers;
    }

    public void setBlockedNumbers(Set<String> blockedNumbers) {
        this.blockedNumbers = blockedNumbers != null ? blockedNumbers : new HashSet<>();
    }

    public boolean isBlocked(String number) {
        return blockedNumbers.contains(number);
    }

    public void blockNumber(String number) {
        blockedNumbers.add(number);
    }

    public void unblockNumber(String number) {
        blockedNumbers.remove(number);
    }
}
