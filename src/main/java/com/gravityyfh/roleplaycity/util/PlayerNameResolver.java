package com.gravityyfh.roleplaycity.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service centralisé pour résoudre les noms de joueurs à partir de leurs UUIDs.
 *
 * Cette classe résout le problème des noms obsolètes quand un joueur change
 * son pseudo sur Mojang. Au lieu de stocker les noms en base de données,
 * les noms sont récupérés dynamiquement via l'UUID.
 *
 * Le cache est mis à jour automatiquement quand un joueur se connecte.
 */
public class PlayerNameResolver {

    // Cache thread-safe pour éviter les appels répétés à Bukkit
    private static final Map<UUID, String> nameCache = new ConcurrentHashMap<>();

    // Nom par défaut si le joueur n'a jamais été vu
    private static final String UNKNOWN_PLAYER = "Inconnu";

    /**
     * Récupère le nom actuel d'un joueur à partir de son UUID.
     * Utilise le cache si disponible, sinon interroge Bukkit.
     *
     * @param uuid L'UUID du joueur
     * @return Le nom actuel du joueur, ou "Inconnu" si non trouvé
     */
    public static String getName(UUID uuid) {
        if (uuid == null) {
            return UNKNOWN_PLAYER;
        }

        // Vérifier d'abord si le joueur est en ligne (nom le plus à jour)
        Player onlinePlayer = Bukkit.getPlayer(uuid);
        if (onlinePlayer != null) {
            String name = onlinePlayer.getName();
            nameCache.put(uuid, name);
            return name;
        }

        // Vérifier le cache
        String cachedName = nameCache.get(uuid);
        if (cachedName != null) {
            return cachedName;
        }

        // Interroger Bukkit pour les joueurs hors ligne
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        String name = offlinePlayer.getName();

        if (name != null) {
            nameCache.put(uuid, name);
            return name;
        }

        return UNKNOWN_PLAYER;
    }

    /**
     * Récupère le nom actuel d'un joueur, avec un nom de fallback
     * si le joueur n'a jamais été vu sur le serveur.
     *
     * @param uuid L'UUID du joueur
     * @param fallbackName Le nom à retourner si le joueur n'est pas trouvé
     * @return Le nom actuel du joueur, ou le fallback
     */
    public static String getName(UUID uuid, String fallbackName) {
        if (uuid == null) {
            return fallbackName != null ? fallbackName : UNKNOWN_PLAYER;
        }

        String name = getName(uuid);
        if (UNKNOWN_PLAYER.equals(name) && fallbackName != null) {
            return fallbackName;
        }
        return name;
    }

    /**
     * Met à jour le cache avec le nom actuel d'un joueur.
     * Appelé quand un joueur se connecte pour s'assurer que le cache est à jour.
     *
     * @param uuid L'UUID du joueur
     * @param name Le nom actuel du joueur
     */
    public static void updateCache(UUID uuid, String name) {
        if (uuid != null && name != null) {
            nameCache.put(uuid, name);
        }
    }

    /**
     * Met à jour le cache avec les informations d'un joueur en ligne.
     *
     * @param player Le joueur en ligne
     */
    public static void updateCache(Player player) {
        if (player != null) {
            nameCache.put(player.getUniqueId(), player.getName());
        }
    }

    /**
     * Invalide l'entrée du cache pour un joueur spécifique.
     *
     * @param uuid L'UUID du joueur
     */
    public static void invalidateCache(UUID uuid) {
        if (uuid != null) {
            nameCache.remove(uuid);
        }
    }

    /**
     * Vide complètement le cache.
     * Utile lors du rechargement du plugin.
     */
    public static void clearCache() {
        nameCache.clear();
    }

    /**
     * Précharge le cache avec tous les joueurs actuellement en ligne.
     * Appelé au démarrage du plugin.
     */
    public static void preloadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateCache(player);
        }
    }

    /**
     * Vérifie si un UUID est dans le cache.
     *
     * @param uuid L'UUID à vérifier
     * @return true si le nom est en cache
     */
    public static boolean isCached(UUID uuid) {
        return uuid != null && nameCache.containsKey(uuid);
    }

    /**
     * Retourne le nombre d'entrées dans le cache.
     *
     * @return Le nombre d'entrées en cache
     */
    public static int getCacheSize() {
        return nameCache.size();
    }
}
