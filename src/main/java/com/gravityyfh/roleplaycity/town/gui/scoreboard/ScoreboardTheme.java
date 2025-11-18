package com.gravityyfh.roleplaycity.town.gui.scoreboard;

import org.bukkit.ChatColor;

/**
 * Thème de couleurs professionnel pour les scoreboards
 * Design moderne et cohérent pour tous les affichages
 */
public class ScoreboardTheme {

    // ========== COULEURS PRINCIPALES ==========

    /**
     * Titre principal (nom de la ville)
     * Exemple: "⭐ VELORIA"
     */
    public static final ChatColor TITLE_COLOR = ChatColor.GOLD;
    public static final boolean TITLE_BOLD = true;

    /**
     * Icône du titre
     */
    public static final String TITLE_ICON = "⭐";

    // ========== SECTIONS ==========

    /**
     * Labels de sections (Type:, Proprio:, etc.)
     */
    public static final ChatColor LABEL_COLOR = ChatColor.GRAY;

    /**
     * Valeurs importantes (noms, données)
     */
    public static final ChatColor VALUE_COLOR = ChatColor.WHITE;

    /**
     * Informations secondaires
     */
    public static final ChatColor SECONDARY_COLOR = ChatColor.DARK_GRAY;

    // ========== INFORMATIONS CLÉS ==========

    /**
     * Numéro de terrain et surface
     * Exemple: "📍 V-042 • 512m²"
     */
    public static final ChatColor INFO_COLOR = ChatColor.YELLOW;

    /**
     * Type de terrain (Professionnel, Particulier, etc.)
     */
    public static final ChatColor TYPE_COLOR = ChatColor.AQUA;

    // ========== ÉCONOMIE ==========

    /**
     * Prix, ventes, montants positifs
     */
    public static final ChatColor PRICE_COLOR = ChatColor.GREEN;

    /**
     * Informations de location
     */
    public static final ChatColor RENT_COLOR = ChatColor.AQUA;

    /**
     * Alertes, urgences, montants négatifs
     */
    public static final ChatColor ALERT_COLOR = ChatColor.RED;

    // ========== ACTEURS ==========

    /**
     * Propriétaires
     */
    public static final ChatColor OWNER_COLOR = ChatColor.YELLOW;

    /**
     * Locataires
     */
    public static final ChatColor RENTER_COLOR = ChatColor.LIGHT_PURPLE;

    /**
     * Entreprises
     */
    public static final ChatColor COMPANY_COLOR = ChatColor.GOLD;

    // ========== ICÔNES ==========
    // Utiliser des symboles Unicode simples compatibles Minecraft
    // Éviter les emojis avec variation selectors (VS16) qui affichent "VS16"

    public static final String ICON_LOCATION = "▸";
    public static final String ICON_PROFESSIONAL = "■";
    public static final String ICON_RESIDENTIAL = "▪";
    public static final String ICON_MUNICIPAL = "◆";
    public static final String ICON_PUBLIC = "※";
    public static final String ICON_OWNER = "►";
    public static final String ICON_RENTER = "▷";
    public static final String ICON_SALE = "◉";
    public static final String ICON_RENT = "◎";
    public static final String ICON_TIME = "⌚";
    public static final String ICON_COMPANY = "■";

    // ========== SÉPARATEURS ==========

    /**
     * Ligne vide pour espacer les sections
     */
    public static final String EMPTY_LINE = " ";

    /**
     * Petit espace pour séparer les infos sur la même ligne
     */
    public static final String SEPARATOR = " • ";

    // ========== HELPERS ==========

    /**
     * Formatte un titre de scoreboard
     */
    public static String formatTitle(String townName) {
        return TITLE_ICON + " " + (TITLE_BOLD ? ChatColor.BOLD : "") + TITLE_COLOR + townName.toUpperCase();
    }

    /**
     * Formatte une ligne de label + valeur
     * Exemple: "Type: Professionnel"
     */
    public static String formatLabelValue(String label, String value) {
        return " " + LABEL_COLOR + label + ": " + VALUE_COLOR + value;
    }

    /**
     * Formatte une ligne de label + valeur avec couleur custom
     */
    public static String formatLabelValue(String label, String value, ChatColor valueColor) {
        return " " + LABEL_COLOR + label + ": " + valueColor + value;
    }

    /**
     * Formatte un prix
     * Exemple: "15,000€"
     */
    public static String formatPrice(double price) {
        if (price >= 1000) {
            return String.format("%,.0f€", price);
        }
        return String.format("%.0f€", price);
    }

    /**
     * Formatte un prix par jour
     * Exemple: "50€/j"
     */
    public static String formatPricePerDay(double price) {
        return String.format("%.0f€/j", price);
    }

    /**
     * Formatte une surface
     * Exemple: "512m²"
     */
    public static String formatSurface(int surface) {
        return surface + "m²";
    }

    /**
     * Formatte un nombre de jours (version simple)
     * Exemple: "15j"
     * @deprecated Utiliser formatRentTime() pour afficher jours/heures/minutes
     */
    @Deprecated
    public static String formatDays(int days) {
        return days + "j";
    }

    /**
     * Formatte le temps restant de location (jours, heures, minutes)
     * Format compact pour scoreboard
     * Exemples: "5j 3h 30m", "18h 45m", "30m"
     */
    public static String formatRentTime(int days, int hours, int minutes) {
        if (days > 0) {
            if (hours > 0 && minutes > 0) {
                return days + "j " + hours + "h " + minutes + "m";
            } else if (hours > 0) {
                return days + "j " + hours + "h";
            } else if (minutes > 0) {
                return days + "j " + minutes + "m";
            }
            return days + "j";
        } else if (hours > 0) {
            if (minutes > 0) {
                return hours + "h " + minutes + "m";
            }
            return hours + "h";
        } else {
            return minutes + "m";
        }
    }

    /**
     * Tronque un texte si trop long (pour éviter les débordements)
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Crée une ligne d'indentation
     */
    public static String indent(String text) {
        return " " + text;
    }

    /**
     * Crée une ligne avec icône
     */
    public static String withIcon(String icon, String text) {
        return icon + " " + text;
    }

    /**
     * Crée une ligne avec icône et couleur
     */
    public static String withIcon(String icon, ChatColor color, String text) {
        return icon + " " + color + text;
    }
}
