package com.gravityyfh.roleplaycity.phone.model;

/**
 * Represente un SMS
 */
public class Message {

    private int id;
    private String senderNumber;
    private String recipientNumber;
    private String content;
    private long sentAt;
    private boolean isRead;

    public Message() {
    }

    public Message(String senderNumber, String recipientNumber, String content) {
        this.senderNumber = senderNumber;
        this.recipientNumber = recipientNumber;
        this.content = content;
        this.sentAt = System.currentTimeMillis();
        this.isRead = false;
    }

    public Message(int id, String senderNumber, String recipientNumber, String content, long sentAt, boolean isRead) {
        this.id = id;
        this.senderNumber = senderNumber;
        this.recipientNumber = recipientNumber;
        this.content = content;
        this.sentAt = sentAt;
        this.isRead = isRead;
    }

    // Getters et Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSenderNumber() {
        return senderNumber;
    }

    public void setSenderNumber(String senderNumber) {
        this.senderNumber = senderNumber;
    }

    public String getRecipientNumber() {
        return recipientNumber;
    }

    public void setRecipientNumber(String recipientNumber) {
        this.recipientNumber = recipientNumber;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public long getSentAt() {
        return sentAt;
    }

    public void setSentAt(long sentAt) {
        this.sentAt = sentAt;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    /**
     * Retourne un apercu du message (premiers 30 caracteres)
     */
    public String getPreview() {
        if (content == null) return "";
        return content.length() > 30 ? content.substring(0, 30) + "..." : content;
    }
}
