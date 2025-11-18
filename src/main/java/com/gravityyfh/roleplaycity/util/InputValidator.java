package com.gravityyfh.roleplaycity.util;

import org.bukkit.ChatColor;

/**
 * FIX BASSE #32: Validation et limitation de la longueur des inputs utilisateur
 *
 * Prévient les exploits mémoire et assure la qualité des données.
 */
public class InputValidator {

    private InputValidator() {
        throw new UnsupportedOperationException("Utility class");
    }

    // Limites de longueur (depuis ConfigDefaults)
    private static final int MIN_COMPANY_NAME_LENGTH = ConfigDefaults.VALIDATION_MIN_COMPANY_NAME_LENGTH;
    private static final int MAX_COMPANY_NAME_LENGTH = ConfigDefaults.VALIDATION_MAX_COMPANY_NAME_LENGTH;
    private static final int MAX_TRANSACTION_DESC_LENGTH = ConfigDefaults.VALIDATION_MAX_TRANSACTION_DESC_LENGTH;
    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_TOWN_NAME_LENGTH = 32;
    private static final int MAX_PLAYER_NAME_LENGTH = 16;

    /**
     * Résultat de validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final String sanitizedValue;

        private ValidationResult(boolean valid, String errorMessage, String sanitizedValue) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.sanitizedValue = sanitizedValue;
        }

        public static ValidationResult success(String sanitizedValue) {
            return new ValidationResult(true, null, sanitizedValue);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, null);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public String getSanitizedValue() { return sanitizedValue; }
    }

    /**
     * Valide et sanitize un nom d'entreprise
     */
    public static ValidationResult validateCompanyName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Le nom de l'entreprise ne peut pas être vide");
        }

        String trimmed = name.trim();

        if (trimmed.length() < MIN_COMPANY_NAME_LENGTH) {
            return ValidationResult.error(String.format(
                "Le nom doit contenir au moins %d caractères", MIN_COMPANY_NAME_LENGTH
            ));
        }

        if (trimmed.length() > MAX_COMPANY_NAME_LENGTH) {
            return ValidationResult.error(String.format(
                "Le nom ne peut pas dépasser %d caractères", MAX_COMPANY_NAME_LENGTH
            ));
        }

        // Sanitize: retirer les caractères interdits
        String sanitized = sanitizeFileName(trimmed);

        // Vérifier qu'il reste quelque chose après sanitization
        if (sanitized.isEmpty()) {
            return ValidationResult.error("Le nom contient uniquement des caractères invalides");
        }

        return ValidationResult.success(sanitized);
    }

    /**
     * Valide et tronque une description de transaction
     */
    public static ValidationResult validateTransactionDescription(String description) {
        if (description == null) {
            return ValidationResult.success("");
        }

        String sanitized = ChatColor.stripColor(description.trim());

        // Tronquer si trop long
        if (sanitized.length() > MAX_TRANSACTION_DESC_LENGTH) {
            sanitized = sanitized.substring(0, MAX_TRANSACTION_DESC_LENGTH) + "...";
        }

        return ValidationResult.success(sanitized);
    }

    /**
     * Valide et tronque une description générale
     */
    public static ValidationResult validateDescription(String description) {
        if (description == null) {
            return ValidationResult.success("");
        }

        String sanitized = description.trim();

        // Tronquer si trop long
        if (sanitized.length() > MAX_DESCRIPTION_LENGTH) {
            sanitized = sanitized.substring(0, MAX_DESCRIPTION_LENGTH);
        }

        return ValidationResult.success(sanitized);
    }

    /**
     * Valide un nom de ville
     */
    public static ValidationResult validateTownName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Le nom de la ville ne peut pas être vide");
        }

        String trimmed = name.trim();

        if (trimmed.length() < 3) {
            return ValidationResult.error("Le nom doit contenir au moins 3 caractères");
        }

        if (trimmed.length() > MAX_TOWN_NAME_LENGTH) {
            return ValidationResult.error(String.format(
                "Le nom ne peut pas dépasser %d caractères", MAX_TOWN_NAME_LENGTH
            ));
        }

        String sanitized = sanitizeFileName(trimmed);

        if (sanitized.isEmpty()) {
            return ValidationResult.error("Le nom contient uniquement des caractères invalides");
        }

        return ValidationResult.success(sanitized);
    }

    /**
     * Sanitize un nom pour l'utiliser dans un nom de fichier
     * Retire les caractères interdits: / \ : * ? " < > |
     */
    public static String sanitizeFileName(String input) {
        if (input == null) return "";
        return input.replaceAll("[/\\\\:*?\"<>|]", "").trim();
    }

    /**
     * Valide un montant monétaire
     */
    public static ValidationResult validateAmount(double amount, double min, double max) {
        if (Double.isNaN(amount) || Double.isInfinite(amount)) {
            return ValidationResult.error("Montant invalide");
        }

        if (amount < min) {
            return ValidationResult.error(String.format(
                "Le montant doit être au moins %.2f€", min
            ));
        }

        if (amount > max) {
            return ValidationResult.error(String.format(
                "Le montant ne peut pas dépasser %.2f€", max
            ));
        }

        return ValidationResult.success(String.valueOf(amount));
    }

    /**
     * Valide un nom de joueur Minecraft
     */
    public static ValidationResult validatePlayerName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.error("Le nom du joueur ne peut pas être vide");
        }

        String trimmed = name.trim();

        if (trimmed.length() > MAX_PLAYER_NAME_LENGTH) {
            return ValidationResult.error("Nom de joueur trop long");
        }

        // Noms Minecraft: alphanumériques + underscore uniquement
        if (!trimmed.matches("^[a-zA-Z0-9_]+$")) {
            return ValidationResult.error("Le nom contient des caractères invalides");
        }

        return ValidationResult.success(trimmed);
    }

    /**
     * Valide un SIRET (9 chiffres)
     */
    public static ValidationResult validateSiret(String siret) {
        if (siret == null || siret.trim().isEmpty()) {
            return ValidationResult.error("Le SIRET ne peut pas être vide");
        }

        String trimmed = siret.trim();

        if (!trimmed.matches("^[0-9]{9}$")) {
            return ValidationResult.error("Le SIRET doit contenir exactement 9 chiffres");
        }

        return ValidationResult.success(trimmed);
    }

    /**
     * Tronque une chaîne à une longueur maximale
     */
    public static String truncate(String input, int maxLength) {
        if (input == null) return "";
        if (input.length() <= maxLength) return input;
        return input.substring(0, maxLength);
    }

    /**
     * Nettoie les codes couleur d'une chaîne
     */
    public static String stripColors(String input) {
        if (input == null) return "";
        return ChatColor.stripColor(input);
    }
}
