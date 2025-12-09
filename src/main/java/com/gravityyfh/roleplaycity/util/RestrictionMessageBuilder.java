package com.gravityyfh.roleplaycity.util;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Générateur de messages contextuels pour les restrictions d'entreprise.
 * Crée des messages adaptés à la situation du joueur avec un design moderne.
 */
public class RestrictionMessageBuilder {

    // Symboles et design
    private static final String BORDER_TOP = ChatColor.DARK_GRAY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    private static final String BORDER_BOTTOM = ChatColor.DARK_GRAY + "▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
    private static final String ICON_LOCK = ChatColor.RED + "🔒";
    private static final String ICON_INFO = ChatColor.YELLOW + "ℹ";
    private static final String ICON_TIP = ChatColor.GREEN + "💡";
    private static final String ICON_QUOTA = ChatColor.GOLD + "📊";

    /**
     * Scénario : Le joueur est MEMBRE de l'entreprise concernée et EN SERVICE, mais le quota de l'entreprise est atteint.
     */
    public static void sendMemberInServiceQuotaReached(Player player, String entrepriseType, int currentQuota, int maxQuota, String actionName) {
        List<String> lines = new ArrayList<>();

        lines.add("");
        lines.add(BORDER_TOP);
        lines.add(ICON_LOCK + " " + ChatColor.RED + ChatColor.BOLD + "QUOTA ATTEINT");
        lines.add("");
        lines.add(ChatColor.GRAY + "Votre entreprise " + ChatColor.GOLD + entrepriseType + ChatColor.GRAY + " a atteint");
        lines.add(ChatColor.GRAY + "sa limite de production horaire.");
        lines.add("");
        lines.add(ICON_QUOTA + " " + ChatColor.WHITE + "Quota : " + ChatColor.RED + currentQuota + ChatColor.GRAY + "/" + ChatColor.GREEN + maxQuota);
        lines.add("");
        lines.add(ICON_TIP + ChatColor.YELLOW + " Solutions :");
        lines.add(ChatColor.GRAY + " • " + ChatColor.GREEN + "Améliorez votre entreprise" + ChatColor.GRAY + " avec");
        lines.add(ChatColor.GRAY + "   " + ChatColor.WHITE + "/entreprise gui" + ChatColor.GRAY + " pour augmenter la limite");
        lines.add(ChatColor.GRAY + " • " + ChatColor.AQUA + "Attendez la prochaine heure" + ChatColor.GRAY + " pour");
        lines.add(ChatColor.GRAY + "   que le quota se réinitialise");
        lines.add(BORDER_BOTTOM);
        lines.add("");

        lines.forEach(player::sendMessage);
    }

    /**
     * Scénario : Le joueur est MEMBRE de l'entreprise concernée mais HORS SERVICE, le quota non-membre est atteint.
     */
    public static void sendMemberOffServiceQuotaReached(Player player, String entrepriseType, int currentQuota, int maxQuota, String entrepriseName) {
        List<String> lines = new ArrayList<>();

        lines.add("");
        lines.add(BORDER_TOP);
        lines.add(ICON_LOCK + " " + ChatColor.RED + ChatColor.BOLD + "QUOTA HORS SERVICE ATTEINT");
        lines.add("");
        lines.add(ChatColor.GRAY + "Vous êtes membre de l'entreprise");
        lines.add(ChatColor.GOLD + entrepriseName + ChatColor.GRAY + " mais pas en service.");
        lines.add("");
        lines.add(ICON_QUOTA + " " + ChatColor.WHITE + "Quota hors service : " + ChatColor.RED + currentQuota + ChatColor.GRAY + "/" + ChatColor.GREEN + maxQuota);
        lines.add("");
        lines.add(ICON_TIP + ChatColor.YELLOW + " Solution :");
        lines.add(ChatColor.GRAY + " • " + ChatColor.GREEN + ChatColor.BOLD + "Passez en mode service" + ChatColor.GRAY + " avec");
        lines.add(ChatColor.GRAY + "   " + ChatColor.WHITE + "/entreprise -> Mes entreprises -> NomEntreprise -> Mode Service ON/OFF");
        lines.add(ChatColor.GRAY + " • " + ChatColor.AQUA + "Quotas bien plus élevés" + ChatColor.GRAY + " en service !");
        lines.add(ChatColor.GRAY + " • " + ChatColor.GOLD + "Revenus automatiques" + ChatColor.GRAY + " pour l'entreprise");
        lines.add(BORDER_BOTTOM);
        lines.add("");

        lines.forEach(player::sendMessage);
    }

    /**
     * Scénario : Le joueur est membre d'une AUTRE entreprise (pas du bon type).
     * FIX MULTI-ENTREPRISES: Version qui affiche toutes les entreprises du joueur
     */
    public static void sendWrongEnterpriseType(Player player, String requiredType, List<String> playerEnterpriseInfos, int maxQuota) {
        List<String> lines = new ArrayList<>();

        lines.add("");
        lines.add(BORDER_TOP);
        lines.add(ICON_LOCK + " " + ChatColor.RED + ChatColor.BOLD + "ENTREPRISE NON AUTORISÉE");
        lines.add("");
        lines.add(ChatColor.GRAY + "Cette activité est réservée aux entreprises");
        lines.add("" + ChatColor.GOLD + ChatColor.BOLD + requiredType + ChatColor.GRAY + ".");
        lines.add("");

        if (playerEnterpriseInfos != null && !playerEnterpriseInfos.isEmpty()) {
            if (playerEnterpriseInfos.size() == 1) {
                lines.add(ICON_INFO + " " + ChatColor.WHITE + "Votre entreprise actuelle :");
                lines.add(ChatColor.GRAY + " • " + ChatColor.GOLD + playerEnterpriseInfos.get(0));
            } else {
                lines.add(ICON_INFO + " " + ChatColor.WHITE + "Vos entreprises actuelles :");
                for (String info : playerEnterpriseInfos) {
                    lines.add(ChatColor.GRAY + " • " + ChatColor.GOLD + info);
                }
            }
        }

        lines.add("");
        lines.add(ICON_TIP + ChatColor.YELLOW + " Solutions :");
        lines.add(ChatColor.GRAY + " • " + ChatColor.GREEN + "Créez une entreprise " + requiredType);
        lines.add(ChatColor.GRAY + " • " + ChatColor.AQUA + "Rejoignez une entreprise " + requiredType);
        lines.add(ChatColor.GRAY + " • " + ChatColor.YELLOW + "Limite : " + maxQuota + " actions/heure" + ChatColor.GRAY + " hors entreprise");
        lines.add(BORDER_BOTTOM);
        lines.add("");

        lines.forEach(player::sendMessage);
    }

    /**
     * Scénario : Le joueur n'a AUCUNE entreprise et le quota non-membre est atteint.
     */
    public static void sendNoEnterpriseQuotaReached(Player player, String requiredType, int currentQuota, int maxQuota) {
        List<String> lines = new ArrayList<>();

        lines.add("");
        lines.add(BORDER_TOP);
        lines.add(ICON_LOCK + " " + ChatColor.RED + ChatColor.BOLD + "QUOTA NON-MEMBRE ATTEINT");
        lines.add("");
        lines.add(ChatColor.GRAY + "Cette activité est limitée pour les joueurs");
        lines.add(ChatColor.GRAY + "sans entreprise " + ChatColor.GOLD + requiredType + ChatColor.GRAY + ".");
        lines.add("");
        lines.add(ICON_QUOTA + " " + ChatColor.WHITE + "Quota non-membre : " + ChatColor.RED + currentQuota + ChatColor.GRAY + "/" + ChatColor.GREEN + maxQuota);
        lines.add("");
        lines.add(ICON_TIP + ChatColor.YELLOW + " Solutions :");
        lines.add(ChatColor.GRAY + " • " + ChatColor.GREEN + ChatColor.BOLD + "Créez votre entreprise");
        lines.add(ChatColor.GRAY + " • " + ChatColor.AQUA + ChatColor.BOLD + "Rejoignez une entreprise");
        lines.add("");
        lines.add(ChatColor.GRAY + " ➜ " + ChatColor.YELLOW + "Quotas augmentés" + ChatColor.GRAY + " une fois en entreprise !");
        lines.add(BORDER_BOTTOM);
        lines.add("");

        lines.forEach(player::sendMessage);
    }

    /**
     * Scénario : Limite = 0 (blocage total) pour les non-membres.
     * FIX MULTI-ENTREPRISES: Version qui affiche toutes les entreprises du joueur
     */
    public static void sendTotallyBlocked(Player player, String requiredType, List<String> playerEnterpriseInfos) {
        List<String> lines = new ArrayList<>();

        lines.add("");
        lines.add(BORDER_TOP);
        lines.add(ICON_LOCK + " " + ChatColor.DARK_RED + ChatColor.BOLD + "ACCÈS RÉSERVÉ");
        lines.add("");
        lines.add(ChatColor.GRAY + "Cette activité est " + ChatColor.RED + ChatColor.BOLD + "exclusivement");
        lines.add(ChatColor.GRAY + "réservée aux entreprises " + ChatColor.GOLD + ChatColor.BOLD + requiredType + ChatColor.GRAY + ".");
        lines.add("");

        if (playerEnterpriseInfos != null && !playerEnterpriseInfos.isEmpty()) {
            if (playerEnterpriseInfos.size() == 1) {
                lines.add(ICON_INFO + " " + ChatColor.WHITE + "Votre entreprise : " + ChatColor.YELLOW + playerEnterpriseInfos.get(0));
            } else {
                lines.add(ICON_INFO + " " + ChatColor.WHITE + "Vos entreprises :");
                for (String info : playerEnterpriseInfos) {
                    lines.add(ChatColor.GRAY + " • " + ChatColor.YELLOW + info);
                }
            }
            lines.add("");

            // FIX COHÉRENCE: Vérifier si le joueur possède déjà une entreprise du type requis
            boolean hasRequiredType = false;
            for (String info : playerEnterpriseInfos) {
                if (info.contains("(" + requiredType + ")")) {
                    hasRequiredType = true;
                    break;
                }
            }

            lines.add(ICON_TIP + ChatColor.YELLOW + " Solution :");
            if (hasRequiredType) {
                // Le joueur a déjà une entreprise du bon type
                lines.add(ChatColor.GRAY + " • " + ChatColor.GREEN + "Passez en mode service avec votre entreprise " + requiredType);
            } else {
                // Le joueur n'a pas d'entreprise du bon type
                lines.add(ChatColor.GRAY + " • " + ChatColor.GREEN + "Créez une entreprise " + requiredType);
            }
        } else {
            lines.add(ICON_TIP + ChatColor.YELLOW + " Solutions :");
            lines.add(ChatColor.GRAY + " • " + ChatColor.GREEN + "Créez une entreprise " + requiredType);
            lines.add(ChatColor.GRAY + " • " + ChatColor.AQUA + "Rejoignez une entreprise " + requiredType);
        }

        lines.add(BORDER_BOTTOM);
        lines.add("");

        lines.forEach(player::sendMessage);
    }

    /**
     * Détermine et envoie le message approprié selon le contexte du joueur.
     * FIX MULTI-ENTREPRISES: Version qui gère plusieurs entreprises
     */
    public static void sendContextualMessage(
        Player player,
        String playerEnterpriseType,
        String playerEnterpriseName,
        boolean isInService,
        String restrictionEnterpriseType,
        int currentQuota,
        int quotaLimit,
        String actionName,
        List<String> allPlayerEnterpriseInfos
    ) {
        boolean hasEnterprise = playerEnterpriseType != null && playerEnterpriseName != null;

        // Cas 1: Limite = 0 (blocage total)
        if (quotaLimit == 0) {
            sendTotallyBlocked(
                player,
                restrictionEnterpriseType,
                allPlayerEnterpriseInfos
            );
            return;
        }

        // Cas 2: Joueur sans entreprise
        if (!hasEnterprise) {
            sendNoEnterpriseQuotaReached(player, restrictionEnterpriseType, currentQuota, quotaLimit);
            return;
        }

        // Cas 3: Joueur dans la bonne entreprise, en service
        if (playerEnterpriseType.equals(restrictionEnterpriseType) && isInService) {
            sendMemberInServiceQuotaReached(player, restrictionEnterpriseType, currentQuota, quotaLimit, actionName);
            return;
        }

        // Cas 4: Joueur dans la bonne entreprise, hors service
        if (playerEnterpriseType.equals(restrictionEnterpriseType) && !isInService) {
            sendMemberOffServiceQuotaReached(player, restrictionEnterpriseType, currentQuota, quotaLimit, playerEnterpriseName);
            return;
        }

        // Cas 5: Joueur dans une autre entreprise
        sendWrongEnterpriseType(
            player,
            restrictionEnterpriseType,
            allPlayerEnterpriseInfos,
            quotaLimit
        );
    }
}
