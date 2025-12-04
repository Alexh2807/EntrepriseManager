package com.gravityyfh.roleplaycity.service;

import java.util.UUID;

/**
 * Représente une requête d'interaction entre deux joueurs
 * (fouille, demande d'identité, etc.)
 */
public class InteractionRequest {

    public enum RequestType {
        FRISK,          // Police veut fouiller
        REQUEST_ID,     // Police demande l'identité
        SHOW_ID         // Joueur veut montrer son ID
    }

    private final UUID requestId;
    private final UUID requesterId;    // Qui fait la demande
    private final String requesterName;
    private final UUID targetId;       // À qui on demande
    private final String targetName;
    private final RequestType type;
    private final long timestamp;
    private final long expirationTime; // En ms

    public InteractionRequest(UUID requesterId, String requesterName, UUID targetId, String targetName, RequestType type) {
        this.requestId = UUID.randomUUID();
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.targetId = targetId;
        this.targetName = targetName;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.expirationTime = 30000; // 30 secondes par défaut
    }

    public UUID getRequestId() {
        return requestId;
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public RequestType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > expirationTime;
    }

    public int getRemainingSeconds() {
        long remaining = expirationTime - (System.currentTimeMillis() - timestamp);
        return Math.max(0, (int) (remaining / 1000));
    }
}
