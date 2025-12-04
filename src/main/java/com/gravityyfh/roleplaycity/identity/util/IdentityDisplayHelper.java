package com.gravityyfh.roleplaycity.identity.util;

import com.gravityyfh.roleplaycity.RoleplayCity;
import com.gravityyfh.roleplaycity.identity.data.Identity;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Utilitaire pour afficher le nom d'identité dans les GUIs
 *
 * IMPORTANT: Cet utilitaire est UNIQUEMENT pour l'AFFICHAGE.
 * Toujours utiliser l'UUID pour identifier les joueurs en interne !
 *
 * Règles:
 * - Si le joueur a une identité → "Prénom Nom"
 * - Si le joueur n'a pas d'identité → Nom Minecraft réel
 */
public class IdentityDisplayHelper {

    private static RoleplayCity plugin;

    /**
     * Initialise le helper avec la référence au plugin
     */
    public static void init(RoleplayCity pluginInstance) {
        plugin = pluginInstance;
    }

    /**
     * Obtient le nom d'affichage pour un joueur en ligne
     * @param player Le joueur
     * @return Le nom d'identité ou "Inconnu_XXX"
     */
    public static String getDisplayName(Player player) {
        if (player == null) return "Inconnu";
        return getDisplayName(player.getUniqueId());
    }

    /**
     * Obtient le nom d'affichage pour un UUID
     * @param uuid L'UUID du joueur
     * @return Le nom d'identité ou le nom Minecraft réel
     */
    public static String getDisplayName(UUID uuid) {
        if (uuid == null || plugin == null) return "Inconnu";

        // Vérifier si le joueur a une identité
        if (plugin.getIdentityManager() != null) {
            Identity identity = plugin.getIdentityManager().getIdentity(uuid);
            if (identity != null && identity.getFirstName() != null && identity.getLastName() != null) {
                return identity.getFirstName() + " " + identity.getLastName();
            }
        }

        // Pas d'identité → Utiliser le vrai nom Minecraft
        return getRealName(uuid);
    }

    /**
     * Obtient le nom d'affichage pour un nom de joueur (recherche par nom)
     * @param playerName Le nom Minecraft du joueur
     * @return Le nom d'identité ou "Inconnu_XXX"
     */
    public static String getDisplayNameByPlayerName(String playerName) {
        if (playerName == null || playerName.isEmpty()) return "Inconnu";

        // Chercher le joueur en ligne d'abord
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return getDisplayName(online.getUniqueId());
        }

        // Chercher dans les joueurs offline
        @SuppressWarnings("deprecation")
        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline.hasPlayedBefore()) {
            return getDisplayName(offline.getUniqueId());
        }

        // Joueur inconnu
        return "Inconnu";
    }

    /**
     * Vérifie si un joueur a une identité
     */
    public static boolean hasIdentity(UUID uuid) {
        if (uuid == null || plugin == null || plugin.getIdentityManager() == null) {
            return false;
        }
        return plugin.getIdentityManager().hasIdentity(uuid);
    }

    /**
     * Obtient le prénom seul (ou vide si pas d'identité)
     */
    public static String getFirstName(UUID uuid) {
        if (uuid == null || plugin == null || plugin.getIdentityManager() == null) {
            return "";
        }
        Identity identity = plugin.getIdentityManager().getIdentity(uuid);
        if (identity != null && identity.getFirstName() != null) {
            return identity.getFirstName();
        }
        return "";
    }

    /**
     * Obtient le nom seul (ou vide si pas d'identité)
     */
    public static String getLastName(UUID uuid) {
        if (uuid == null || plugin == null || plugin.getIdentityManager() == null) {
            return "";
        }
        Identity identity = plugin.getIdentityManager().getIdentity(uuid);
        if (identity != null && identity.getLastName() != null) {
            return identity.getLastName();
        }
        return "";
    }

    /**
     * Obtient le vrai nom Minecraft du joueur (pour logs/debug)
     */
    public static String getRealName(UUID uuid) {
        if (uuid == null) return "Unknown";

        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    // ═══════════════════════════════════════════════════════════════════
    // SYSTÈME DE TRADUCTION POUR TAB COMPLETER
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Trouve un joueur en ligne par son nom d'identité OU son nom Minecraft
     * Utile pour les commandes qui acceptent les deux formats
     * @param input Le nom entré (peut être "Jean Dupont" ou "PlayerName")
     * @return Le joueur trouvé, ou null si non trouvé
     */
    public static Player findPlayerByNameOrIdentity(String input) {
        if (input == null || input.isEmpty()) return null;

        // 1. D'abord essayer par nom Minecraft exact
        Player byName = Bukkit.getPlayerExact(input);
        if (byName != null) return byName;

        // 2. Ensuite chercher par nom d'identité parmi les joueurs en ligne
        for (Player online : Bukkit.getOnlinePlayers()) {
            String displayName = getDisplayName(online.getUniqueId());
            if (displayName.equalsIgnoreCase(input)) {
                return online;
            }
        }

        // 3. Recherche partielle (commence par)
        String lowerInput = input.toLowerCase();
        for (Player online : Bukkit.getOnlinePlayers()) {
            // Par nom Minecraft
            if (online.getName().toLowerCase().startsWith(lowerInput)) {
                return online;
            }
            // Par nom d'identité
            String displayName = getDisplayName(online.getUniqueId());
            if (displayName.toLowerCase().startsWith(lowerInput)) {
                return online;
            }
        }

        return null;
    }

    /**
     * Génère une liste de suggestions pour TabCompleter
     * Inclut les noms d'identité ET les noms Minecraft
     * @param partialInput L'entrée partielle de l'utilisateur
     * @return Liste de suggestions (noms d'identité + noms Minecraft)
     */
    public static java.util.List<String> getTabCompletions(String partialInput) {
        java.util.List<String> completions = new java.util.ArrayList<>();
        String lowerInput = partialInput == null ? "" : partialInput.toLowerCase();

        for (Player online : Bukkit.getOnlinePlayers()) {
            String mcName = online.getName();
            String idName = getDisplayName(online.getUniqueId());

            // Ajouter le nom Minecraft s'il correspond
            if (mcName.toLowerCase().startsWith(lowerInput)) {
                completions.add(mcName);
            }

            // Ajouter le nom d'identité s'il est différent et correspond
            if (!idName.equals(mcName) && idName.toLowerCase().startsWith(lowerInput)) {
                // Pour les noms avec espaces, les entourer de guillemets
                if (idName.contains(" ")) {
                    completions.add("\"" + idName + "\"");
                } else {
                    completions.add(idName);
                }
            }
        }

        return completions;
    }

    /**
     * Crée une map de traduction: Nom d'identité → UUID
     * Utile pour résoudre rapidement les noms d'identité
     * @return Map<NomIdentité, UUID>
     */
    public static java.util.Map<String, UUID> buildIdentityToUuidMap() {
        java.util.Map<String, UUID> map = new java.util.HashMap<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            String displayName = getDisplayName(online.getUniqueId());
            map.put(displayName.toLowerCase(), online.getUniqueId());
            // Aussi ajouter le nom Minecraft
            map.put(online.getName().toLowerCase(), online.getUniqueId());
        }

        return map;
    }

    /**
     * Génère une liste formatée pour affichage: "NomIdentité (pseudo_mc)"
     * Utile pour les menus de sélection où on veut montrer les deux
     * @return Liste de strings formatées
     */
    public static java.util.List<String> getFormattedPlayerList() {
        java.util.List<String> list = new java.util.ArrayList<>();

        for (Player online : Bukkit.getOnlinePlayers()) {
            String mcName = online.getName();
            String idName = getDisplayName(online.getUniqueId());

            if (idName.equals(mcName)) {
                // Pas d'identité ou identité = nom MC → afficher juste le nom MC
                list.add(mcName);
            } else {
                // A une identité différente → afficher "Identité (pseudo_mc)"
                list.add(idName + " (" + mcName + ")");
            }
        }

        return list;
    }
}
