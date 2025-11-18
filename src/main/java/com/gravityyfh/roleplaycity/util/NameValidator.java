package com.gravityyfh.roleplaycity.util;

import org.bukkit.plugin.Plugin;

import java.util.regex.Pattern;

/**
 * FIX BASSE #31: Validation sécurisée des noms d'entités
 *
 * Valide tous les noms d'entités (entreprises, villes, plots, etc.)
 * pour prévenir les exploits de sécurité et garantir la cohérence.
 */
public class NameValidator {

    private final Plugin plugin;

    // Patterns de sécurité
    private static final Pattern SAFE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9àâäéèêëïîôùûüÿçÀÂÄÉÈÊËÏÎÔÙÛÜŸÇ\\s\\-_']+$");
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile("('.*(\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|EXECUTE|UNION|SCRIPT)\\b).*')|(--)|(;)|(\\|\\|)|(&&)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_INJECTION_PATTERN = Pattern.compile("[$`&|;<>{}\\[\\]\\\\]");
    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile("(\\.\\./)|(\\.\\\\)|(\\.\\.%2[fF])");
    private static final Pattern XSS_PATTERN = Pattern.compile("<[^>]*>|javascript:|on\\w+\\s*=", Pattern.CASE_INSENSITIVE);

    // Limites de longueur (FIX #32 intégré)
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 32;
    private static final int MAX_ENTREPRISE_NAME_LENGTH = 48;
    private static final int MAX_TOWN_NAME_LENGTH = 32;
    private static final int MAX_DESCRIPTION_LENGTH = 256;

    // Mots réservés interdits
    private static final String[] RESERVED_WORDS = {
        "admin", "console", "server", "system", "root", "minecraft",
        "op", "operator", "moderator", "null", "undefined"
    };

    public NameValidator(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
         * Résultat de validation
         */
        public record ValidationResult(boolean valid, String error, String sanitizedName) {

        public static ValidationResult success(String sanitizedName) {
                return new ValidationResult(true, null, sanitizedName);
            }

            public static ValidationResult error(String error) {
                return new ValidationResult(false, error, null);
            }
        }

    /**
     * Valide un nom d'entreprise
     */
    public ValidationResult validateEntrepriseName(String name) {
        if (name == null) {
            return ValidationResult.error("Le nom ne peut pas être null");
        }

        String trimmed = name.trim();

        // Vérifier la longueur
        if (trimmed.length() < MIN_NAME_LENGTH) {
            return ValidationResult.error("Le nom doit contenir au moins " + MIN_NAME_LENGTH + " caractères");
        }
        if (trimmed.length() > MAX_ENTREPRISE_NAME_LENGTH) {
            return ValidationResult.error("Le nom ne peut pas dépasser " + MAX_ENTREPRISE_NAME_LENGTH + " caractères");
        }

        // Vérifier les caractères autorisés
        if (!SAFE_NAME_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.error("Le nom contient des caractères non autorisés (lettres, chiffres, espaces, - _ ' uniquement)");
        }

        // Vérifier les patterns malveillants
        ValidationResult securityCheck = checkSecurityPatterns(trimmed);
        if (!securityCheck.valid()) {
            return securityCheck;
        }

        // Vérifier les mots réservés
        if (isReservedWord(trimmed)) {
            return ValidationResult.error("Ce nom est réservé et ne peut pas être utilisé");
        }

        return ValidationResult.success(trimmed);
    }

    /**
     * Valide un nom de ville
     */
    public ValidationResult validateTownName(String name) {
        if (name == null) {
            return ValidationResult.error("Le nom ne peut pas être null");
        }

        String trimmed = name.trim();

        // Vérifier la longueur
        if (trimmed.length() < MIN_NAME_LENGTH) {
            return ValidationResult.error("Le nom doit contenir au moins " + MIN_NAME_LENGTH + " caractères");
        }
        if (trimmed.length() > MAX_TOWN_NAME_LENGTH) {
            return ValidationResult.error("Le nom ne peut pas dépasser " + MAX_TOWN_NAME_LENGTH + " caractères");
        }

        // Vérifier les caractères autorisés
        if (!SAFE_NAME_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.error("Le nom contient des caractères non autorisés (lettres, chiffres, espaces, - _ ' uniquement)");
        }

        // Vérifier les patterns malveillants
        ValidationResult securityCheck = checkSecurityPatterns(trimmed);
        if (!securityCheck.valid()) {
            return securityCheck;
        }

        // Vérifier les mots réservés
        if (isReservedWord(trimmed)) {
            return ValidationResult.error("Ce nom est réservé et ne peut pas être utilisé");
        }

        return ValidationResult.success(trimmed);
    }

    /**
     * Valide un nom générique (plot, groupe, etc.)
     */
    public ValidationResult validateGenericName(String name) {
        if (name == null) {
            return ValidationResult.error("Le nom ne peut pas être null");
        }

        String trimmed = name.trim();

        // Vérifier la longueur
        if (trimmed.length() < MIN_NAME_LENGTH) {
            return ValidationResult.error("Le nom doit contenir au moins " + MIN_NAME_LENGTH + " caractères");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return ValidationResult.error("Le nom ne peut pas dépasser " + MAX_NAME_LENGTH + " caractères");
        }

        // Vérifier les caractères autorisés
        if (!SAFE_NAME_PATTERN.matcher(trimmed).matches()) {
            return ValidationResult.error("Le nom contient des caractères non autorisés (lettres, chiffres, espaces, - _ ' uniquement)");
        }

        // Vérifier les patterns malveillants
        ValidationResult securityCheck = checkSecurityPatterns(trimmed);
        if (!securityCheck.valid()) {
            return securityCheck;
        }

        return ValidationResult.success(trimmed);
    }

    /**
     * Valide une description
     */
    public ValidationResult validateDescription(String description) {
        if (description == null) {
            return ValidationResult.success(""); // Description peut être vide
        }

        String trimmed = description.trim();

        // Vérifier la longueur
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            return ValidationResult.error("La description ne peut pas dépasser " + MAX_DESCRIPTION_LENGTH + " caractères");
        }

        // Vérifier les patterns malveillants (moins strict pour les descriptions)
        if (SQL_INJECTION_PATTERN.matcher(trimmed).find()) {
            return ValidationResult.error("La description contient des caractères suspects");
        }

        if (XSS_PATTERN.matcher(trimmed).find()) {
            return ValidationResult.error("La description contient des balises HTML non autorisées");
        }

        return ValidationResult.success(trimmed);
    }

    /**
     * Vérifie les patterns de sécurité
     */
    private ValidationResult checkSecurityPatterns(String name) {
        // SQL Injection
        if (SQL_INJECTION_PATTERN.matcher(name).find()) {
            plugin.getLogger().warning("⚠ Tentative de SQL injection détectée: " + name);
            return ValidationResult.error("Le nom contient des caractères suspects");
        }

        // Command Injection
        if (COMMAND_INJECTION_PATTERN.matcher(name).find()) {
            plugin.getLogger().warning("⚠ Tentative de command injection détectée: " + name);
            return ValidationResult.error("Le nom contient des caractères interdits");
        }

        // Path Traversal
        if (PATH_TRAVERSAL_PATTERN.matcher(name).find()) {
            plugin.getLogger().warning("⚠ Tentative de path traversal détectée: " + name);
            return ValidationResult.error("Le nom contient des séquences interdites");
        }

        // XSS
        if (XSS_PATTERN.matcher(name).find()) {
            plugin.getLogger().warning("⚠ Tentative de XSS détectée: " + name);
            return ValidationResult.error("Le nom contient des balises HTML non autorisées");
        }

        return ValidationResult.success(name);
    }

    /**
     * Vérifie si un nom est un mot réservé
     */
    private boolean isReservedWord(String name) {
        String lowerName = name.toLowerCase();
        for (String reserved : RESERVED_WORDS) {
            if (lowerName.equals(reserved)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nettoie un nom en supprimant les caractères dangereux
     * FIX #33 intégré: Sanitisation
     */
    public String sanitizeName(String name) {
        if (name == null) return "";

        // Trim et normaliser les espaces
        String sanitized = name.trim().replaceAll("\\s+", " ");

        // Supprimer les caractères dangereux
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9àâäéèêëïîôùûüÿçÀÂÄÉÈÊËÏÎÔÙÛÜŸÇ\\s\\-_']", "");

        // Limiter la longueur
        if (sanitized.length() > MAX_NAME_LENGTH) {
            sanitized = sanitized.substring(0, MAX_NAME_LENGTH);
        }

        return sanitized;
    }

    /**
     * Nettoie un nom de fichier pour éviter path traversal
     * FIX #33: Sanitisation des noms de fichiers
     */
    public String sanitizeFileName(String fileName) {
        if (fileName == null) return "unnamed";

        // Supprimer les chemins
        fileName = fileName.replaceAll("[\\\\/]", "");

        // Supprimer les caractères dangereux
        fileName = fileName.replaceAll("[^a-zA-Z0-9_\\-.]", "_");

        // Supprimer les doubles points consécutifs (path traversal)
        fileName = fileName.replaceAll("\\.{2,}", ".");

        // Limiter la longueur
        if (fileName.length() > 64) {
            String extension = "";
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = fileName.substring(dotIndex);
                fileName = fileName.substring(0, Math.min(64 - extension.length(), dotIndex));
            } else {
                fileName = fileName.substring(0, 64);
            }
            fileName = fileName + extension;
        }

        // Vérifier que le nom n'est pas vide
        if (fileName.isEmpty() || fileName.equals(".")) {
            fileName = "unnamed";
        }

        return fileName;
    }

    /**
     * Vérifie si un nom est valide au chargement
     * FIX #31: Validation au chargement
     */
    public boolean isValidLoadedName(String name, String entityType) {
        ValidationResult result;

        switch (entityType.toLowerCase()) {
            case "entreprise":
            case "company":
                result = validateEntrepriseName(name);
                break;
            case "ville":
            case "town":
                result = validateTownName(name);
                break;
            default:
                result = validateGenericName(name);
                break;
        }

        if (!result.valid()) {
            plugin.getLogger().warning("⚠ Entité " + entityType + " avec nom invalide détectée au chargement: " + name);
            plugin.getLogger().warning("   Erreur: " + result.error());
            return false;
        }

        return true;
    }

    // Getters pour les limites (utile pour les GUI)
    public static int getMinNameLength() {
        return MIN_NAME_LENGTH;
    }

    public static int getMaxNameLength() {
        return MAX_NAME_LENGTH;
    }

    public static int getMaxEntrepriseNameLength() {
        return MAX_ENTREPRISE_NAME_LENGTH;
    }

    public static int getMaxTownNameLength() {
        return MAX_TOWN_NAME_LENGTH;
    }

    public static int getMaxDescriptionLength() {
        return MAX_DESCRIPTION_LENGTH;
    }
}
