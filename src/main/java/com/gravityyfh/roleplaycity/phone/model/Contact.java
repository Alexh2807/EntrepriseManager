package com.gravityyfh.roleplaycity.phone.model;

import java.util.UUID;

/**
 * Represente un contact dans le repertoire telephonique d'un joueur
 */
public class Contact {

    private int id;
    private UUID ownerUuid;
    private String contactName;
    private String contactNumber;
    private long createdAt;

    public Contact() {
    }

    public Contact(UUID ownerUuid, String contactName, String contactNumber) {
        this.ownerUuid = ownerUuid;
        this.contactName = contactName;
        this.contactNumber = contactNumber;
        this.createdAt = System.currentTimeMillis();
    }

    public Contact(int id, UUID ownerUuid, String contactName, String contactNumber, long createdAt) {
        this.id = id;
        this.ownerUuid = ownerUuid;
        this.contactName = contactName;
        this.contactNumber = contactNumber;
        this.createdAt = createdAt;
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

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public void setContactNumber(String contactNumber) {
        this.contactNumber = contactNumber;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
