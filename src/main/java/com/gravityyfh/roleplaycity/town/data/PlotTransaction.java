package com.gravityyfh.roleplaycity.town.data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Représente une transaction (vente ou location) de parcelle
 */
public class PlotTransaction {

    public enum TransactionType {
        SALE("Vente"),
        RENT("Location"),
        TAX("Taxe"),
        FINE("Amende"),
        DEPOSIT("Dépôt"),
        WITHDRAWAL("Retrait");

        private final String displayName;

        TransactionType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private final TransactionType type;
    private final UUID playerUuid;
    private final String playerName;
    private final double amount;
    private final LocalDateTime timestamp;
    private final String description;

    public PlotTransaction(TransactionType type, UUID playerUuid, String playerName,
                          double amount, String description) {
        this.type = type;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.amount = amount;
        this.timestamp = LocalDateTime.now();
        this.description = description;
    }

    public TransactionType getType() {
        return type;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public double getAmount() {
        return amount;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s - %s: %.2f€ (%s)",
            timestamp.toLocalDate(),
            type.getDisplayName(),
            playerName,
            amount,
            description
        );
    }
}
