package com.gravityyfh.roleplaycity.util;

import org.bukkit.ChatColor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * FIX BASSE #15: Utilitaire pour le formatage de la monnaie
 *
 * Réduit la duplication de code pour le formatage des montants.
 * Plus de 143 occurrences de String.format("%.2f€") peuvent bénéficier de cette classe.
 *
 * Avantages:
 * - Cohérence du formatage dans tout le plugin
 * - Réduction de la duplication (143+ occurrences)
 * - Facilite la modification du symbole monétaire
 * - Support des grands nombres avec séparateurs
 * - Formatage coloré selon le montant (positif/négatif)
 */
public class CurrencyUtil {

    // Symbole monétaire
    private static final String CURRENCY_SYMBOL = "€";

    // Formats
    private static final DecimalFormat FORMAT_SIMPLE;
    private static final DecimalFormat FORMAT_WITH_SEPARATOR;
    private static final DecimalFormat FORMAT_COMPACT;

    static {
        // Format simple: 1234.56€
        FORMAT_SIMPLE = new DecimalFormat("0.00");
        FORMAT_SIMPLE.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.FRENCH));

        // Format avec séparateurs: 1,234.56€
        FORMAT_WITH_SEPARATOR = new DecimalFormat("#,##0.00");
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRENCH);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        FORMAT_WITH_SEPARATOR.setDecimalFormatSymbols(symbols);

        // Format compact (arrondi): 1.2K€, 1.5M€
        FORMAT_COMPACT = new DecimalFormat("0.#");
        FORMAT_COMPACT.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.FRENCH));
    }

    // === FORMATAGE BASIQUE ===

    /**
     * Formate un montant avec séparateurs de milliers
     * Exemple: 1234.56 -> "1,234.56€"
     *
     * @param amount Montant à formater
     * @return Montant formaté
     */
    public static String format(double amount) {
        return FORMAT_WITH_SEPARATOR.format(amount) + CURRENCY_SYMBOL;
    }

    /**
     * Formate un montant sans séparateurs
     * Exemple: 1234.56 -> "1234.56€"
     *
     * @param amount Montant à formater
     * @return Montant formaté
     */
    public static String formatSimple(double amount) {
        return FORMAT_SIMPLE.format(amount) + CURRENCY_SYMBOL;
    }

    /**
     * Formate un montant de manière compacte
     * Exemple: 1500 -> "1.5K€", 1500000 -> "1.5M€"
     *
     * @param amount Montant à formater
     * @return Montant formaté de manière compacte
     */
    public static String formatCompact(double amount) {
        if (amount >= 1_000_000_000) {
            return FORMAT_COMPACT.format(amount / 1_000_000_000.0) + "B" + CURRENCY_SYMBOL;
        } else if (amount >= 1_000_000) {
            return FORMAT_COMPACT.format(amount / 1_000_000.0) + "M" + CURRENCY_SYMBOL;
        } else if (amount >= 1_000) {
            return FORMAT_COMPACT.format(amount / 1_000.0) + "K" + CURRENCY_SYMBOL;
        } else {
            return formatSimple(amount);
        }
    }

    // === FORMATAGE COLORÉ ===

    /**
     * Formate un montant avec couleur (vert si positif, rouge si négatif)
     *
     * @param amount Montant à formater
     * @return Montant formaté avec couleur
     */
    public static String formatColored(double amount) {
        ChatColor color = amount >= 0 ? ChatColor.GREEN : ChatColor.RED;
        return color + format(amount);
    }

    /**
     * Formate un montant avec couleur et préfixe +/- explicite
     * Exemple: +1,234.56€ (vert), -234.56€ (rouge)
     *
     * @param amount Montant à formater
     * @return Montant formaté avec signe et couleur
     */
    public static String formatColoredWithSign(double amount) {
        ChatColor color = amount >= 0 ? ChatColor.GREEN : ChatColor.RED;
        String sign = amount >= 0 ? "+" : "";
        return color + sign + format(amount);
    }

    /**
     * Formate un montant avec une couleur personnalisée
     *
     * @param amount Montant à formater
     * @param color Couleur à appliquer
     * @return Montant formaté avec couleur
     */
    public static String formatColored(double amount, ChatColor color) {
        return color + format(amount);
    }

    // === FORMATAGE SPÉCIALISÉ ===

    /**
     * Formate un prix (toujours avec séparateurs et couleur dorée)
     * Exemple: "Prix: §61,234.56€"
     *
     * @param amount Montant à formater
     * @return Prix formaté
     */
    public static String formatPrice(double amount) {
        return ChatColor.GOLD + format(amount);
    }

    /**
     * Formate un solde (couleur selon positif/négatif)
     * Exemple: "Solde: §a1,234.56€" ou "Solde: §c-234.56€"
     *
     * @param amount Montant à formater
     * @return Solde formaté
     */
    public static String formatBalance(double amount) {
        return formatColored(amount);
    }

    /**
     * Formate un coût (toujours en rouge)
     *
     * @param amount Montant à formater
     * @return Coût formaté
     */
    public static String formatCost(double amount) {
        return ChatColor.RED + format(amount);
    }

    /**
     * Formate un gain (toujours en vert avec +)
     *
     * @param amount Montant à formater
     * @return Gain formaté
     */
    public static String formatGain(double amount) {
        return ChatColor.GREEN + "+" + format(amount);
    }

    /**
     * Formate une perte (toujours en rouge avec -)
     *
     * @param amount Montant à formater (valeur positive)
     * @return Perte formatée
     */
    public static String formatLoss(double amount) {
        return ChatColor.RED + "-" + format(amount);
    }

    // === MESSAGES COMPLETS ===

    /**
     * Crée un message de transaction réussie
     *
     * @param amount Montant de la transaction
     * @param description Description de la transaction
     * @return Message formaté
     */
    public static String transactionSuccess(double amount, String description) {
        return ChatColor.GREEN + "✅ Transaction réussie: " +
               ChatColor.GRAY + description + " " +
               formatColored(amount);
    }

    /**
     * Crée un message de transaction échouée
     *
     * @param reason Raison de l'échec
     * @return Message formaté
     */
    public static String transactionFailed(String reason) {
        return ChatColor.RED + "❌ Transaction échouée: " + reason;
    }

    /**
     * Crée un message de fonds insuffisants
     *
     * @param required Montant requis
     * @param current Montant actuel
     * @return Message formaté
     */
    public static String insufficientFunds(double required, double current) {
        return ChatColor.RED + "❌ Fonds insuffisants! " +
               ChatColor.GRAY + "Requis: " + formatPrice(required) + " " +
               ChatColor.GRAY + "Vous avez: " + formatBalance(current);
    }

    /**
     * Crée un message d'affichage de solde
     *
     * @param balance Solde actuel
     * @return Message formaté
     */
    public static String displayBalance(double balance) {
        return ChatColor.GRAY + "Solde: " + formatBalance(balance);
    }

    /**
     * Crée un message d'affichage de prix
     *
     * @param price Prix
     * @return Message formaté
     */
    public static String displayPrice(double price) {
        return ChatColor.GRAY + "Prix: " + formatPrice(price);
    }

    // === PARSING ===

    /**
     * Parse une chaîne en montant
     *
     * @param amountStr Chaîne à parser
     * @return Montant parsé, ou 0.0 si échec
     */
    public static double parse(String amountStr) {
        if (amountStr == null || amountStr.isEmpty()) {
            return 0.0;
        }

        // Nettoyer la chaîne (retirer symbole, espaces, couleurs)
        String cleaned = amountStr
            .replace(CURRENCY_SYMBOL, "")
            .replace(",", "")
            .replace(" ", "")
            .replaceAll("§[0-9a-fk-or]", "")  // Codes couleur Minecraft
            .trim();

        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * Vérifie si une chaîne représente un montant valide
     *
     * @param amountStr Chaîne à vérifier
     * @return true si valide
     */
    public static boolean isValidAmount(String amountStr) {
        try {
            double amount = parse(amountStr);
            return amount >= 0 && !Double.isNaN(amount) && !Double.isInfinite(amount);
        } catch (Exception e) {
            return false;
        }
    }

    // === COMPARAISON ===

    /**
     * Compare deux montants avec tolérance pour les erreurs d'arrondi
     *
     * @param amount1 Premier montant
     * @param amount2 Deuxième montant
     * @return true si égaux (à 0.01€ près)
     */
    public static boolean equals(double amount1, double amount2) {
        return Math.abs(amount1 - amount2) < 0.01;
    }

    /**
     * Vérifie si un montant est suffisant pour un coût
     *
     * @param balance Solde disponible
     * @param cost Coût requis
     * @return true si suffisant
     */
    public static boolean canAfford(double balance, double cost) {
        return balance >= cost || equals(balance, cost);
    }

    // === CALCULS UTILITAIRES ===

    /**
     * Arrondit un montant à 2 décimales
     *
     * @param amount Montant à arrondir
     * @return Montant arrondi
     */
    public static double round(double amount) {
        return Math.round(amount * 100.0) / 100.0;
    }

    /**
     * Calcule un pourcentage d'un montant
     *
     * @param amount Montant de base
     * @param percentage Pourcentage (0-100)
     * @return Montant calculé
     */
    public static double percent(double amount, double percentage) {
        return round((amount * percentage) / 100.0);
    }

    /**
     * Calcule une taxe sur un montant
     *
     * @param amount Montant de base
     * @param taxRate Taux de taxe (0-100)
     * @return Montant de la taxe
     */
    public static double calculateTax(double amount, double taxRate) {
        return percent(amount, taxRate);
    }

    /**
     * Calcule le montant après taxe
     *
     * @param amount Montant de base
     * @param taxRate Taux de taxe (0-100)
     * @return Montant total avec taxe
     */
    public static double withTax(double amount, double taxRate) {
        return round(amount + calculateTax(amount, taxRate));
    }
}
