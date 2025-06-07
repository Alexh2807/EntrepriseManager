package com.gravityyfh.entreprisemanager.Models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Transaction {
    public final TransactionType type;
    public final double amount;
    public final String description;
    public final LocalDateTime timestamp;
    public final String initiatedBy;

    public enum TransactionType {
        DEPOSIT("Dépôt de Capital"),
        WITHDRAWAL("Retrait de Capital"),
        REVENUE("Revenu d'Activité"),
        TAXES("Impôts sur Revenu"),
        PRIMES("Paiement des Primes"),
        OTHER_EXPENSE("Autre Dépense Op."),
        OTHER_INCOME("Autre Revenu Op."),
        CREATION_COST("Frais de Création"),
        RENAME_COST("Frais de Renommage"),
        PAYROLL_TAX("Charges Salariales");

        private final String displayName;
        TransactionType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        public boolean isOperationalIncome() { return this == REVENUE || this == OTHER_INCOME; }
        public boolean isOperationalExpense() { return this == TAXES || this == PRIMES || this == OTHER_EXPENSE || this == CREATION_COST || this == RENAME_COST || this == PAYROLL_TAX; }
    }

    public Transaction(TransactionType type, double amount, String description, String initiatedBy) {
        this(type, amount, description, initiatedBy, LocalDateTime.now());
    }

    public Transaction(TransactionType type, double amount, String description, String initiatedBy, LocalDateTime timestamp) {
        this.type = type;
        if (type.isOperationalExpense() && amount > 0) this.amount = -amount;
        else if (type == TransactionType.WITHDRAWAL && amount > 0) this.amount = -amount;
        else this.amount = amount;
        this.description = description != null ? description : "";
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.initiatedBy = initiatedBy != null ? initiatedBy : "System";
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type.name());
        map.put("amount", amount);
        map.put("description", description);
        map.put("timestamp", timestamp.toString());
        map.put("initiatedBy", initiatedBy);
        return map;
    }

    @SuppressWarnings("unchecked")
    public static Transaction deserialize(Map<String, Object> map) {
        try {
            TransactionType transacType = TransactionType.valueOf((String)map.get("type"));
            double transacAmount = ((Number)map.get("amount")).doubleValue();
            String transacDesc = (String)map.getOrDefault("description", "");
            String transacInitiator = (String)map.getOrDefault("initiatedBy", "Unknown");
            LocalDateTime transacTimestamp = LocalDateTime.parse((String)map.get("timestamp"));
            return new Transaction(transacType, transacAmount, transacDesc, transacInitiator, transacTimestamp);
        } catch (Exception e) {
            System.err.println("Erreur désérialisation Transaction: " + e.getMessage() + " pour map: " + map);
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %.2f€ (%s) par %s",
                timestamp.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                type.getDisplayName(), amount, description, initiatedBy);
    }
}