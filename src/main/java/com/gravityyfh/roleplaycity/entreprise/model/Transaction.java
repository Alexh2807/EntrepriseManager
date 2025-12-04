package com.gravityyfh.roleplaycity.entreprise.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Représente une transaction financière d'une entreprise.
 * Toutes les dépenses sont automatiquement converties en montants négatifs.
 */
public class Transaction {
    public final TransactionType type;
    public final double amount;
    public final String description;
    public final LocalDateTime timestamp;
    public final String initiatedBy;

    /**
     * Constructeur principal pour une nouvelle transaction (timestamp = maintenant).
     */
    public Transaction(TransactionType type, double amount, String description, String initiatedBy) {
        this(type, amount, description, initiatedBy, LocalDateTime.now());
    }

    /**
     * Constructeur complet avec timestamp personnalisé (pour désérialisation).
     */
    public Transaction(TransactionType type, double amount, String description, String initiatedBy, LocalDateTime timestamp) {
        this.type = type;

        // Normaliser les montants : dépenses = négatif, revenus = positif
        if (type.isOperationalExpense() && amount > 0) {
            this.amount = -amount;
        } else if (type == TransactionType.WITHDRAWAL && amount > 0) {
            this.amount = -amount;
        } else {
            this.amount = amount;
        }

        this.description = description != null ? description : "";
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.initiatedBy = initiatedBy != null ? initiatedBy : "System";
    }

    /**
     * Sérialise la transaction en Map pour sauvegarde YAML.
     */
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type.name());
        map.put("amount", amount);
        map.put("description", description);
        map.put("timestamp", timestamp.toString());
        map.put("initiatedBy", initiatedBy);
        return map;
    }

    /**
     * Désérialise une transaction depuis une Map YAML.
     * @return Transaction ou null si erreur
     */
    public static Transaction deserialize(Map<String, Object> map) {
        try {
            TransactionType transacType = TransactionType.valueOf((String) map.get("type"));
            double transacAmount = ((Number) map.get("amount")).doubleValue();
            String transacDesc = (String) map.getOrDefault("description", "");
            String transacInitiator = (String) map.getOrDefault("initiatedBy", "Unknown");
            LocalDateTime transacTimestamp = LocalDateTime.parse((String) map.get("timestamp"));

            return new Transaction(transacType, transacAmount, transacDesc, transacInitiator, transacTimestamp);
        } catch (Exception e) {
            // TODO: Logger l'erreur proprement (sans dépendance statique)
            System.err.println("Erreur désérialisation Transaction: " + e.getMessage() + " pour map: " + map);
            return null;
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] %s: %.2f€ (%s) par %s",
            timestamp.format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
            type.getDisplayName(),
            amount,
            description,
            initiatedBy
        );
    }
}
