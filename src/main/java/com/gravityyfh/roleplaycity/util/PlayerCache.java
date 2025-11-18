package com.gravityyfh.roleplaycity.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FIX BASSE #8: Cache des joueurs en ligne pour éviter les appels répétés à Bukkit API
 *
 * Performance:
 * - getOnlinePlayer() : O(1) au lieu de O(n) avec Bukkit.getPlayer()
 * - getOnlinePlayers() : retourne une copie déjà construite au lieu de reconstruire à chaque appel
 * - isOnline() : O(1) au lieu de chercher dans toute la liste
 */
public class PlayerCache implements Listener {

    private final Plugin plugin;

    // Cache principal: UUID -> Player
    private final Map<UUID, Player> playersByUuid;

    // Index secondaire: nom (lowercase) -> Player pour recherche rapide par nom
    private final Map<String, Player> playersByName;

    // Collection en lecture seule pour retour rapide
    private volatile Collection<Player> cachedOnlinePlayers;

    public PlayerCache(Plugin plugin) {
        this.plugin = plugin;
        this.playersByUuid = new ConcurrentHashMap<>();
        this.playersByName = new ConcurrentHashMap<>();
        this.cachedOnlinePlayers = Collections.emptyList();

        // Initialiser le cache avec les joueurs déjà en ligne
        initializeCache();

        plugin.getLogger().info("[PlayerCache] Cache initialisé avec " + playersByUuid.size() + " joueurs");
    }

    /**
     * Initialise le cache avec les joueurs actuellement en ligne
     */
    private void initializeCache() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            addPlayer(player);
        }
    }

    /**
     * Ajoute un joueur au cache
     */
    private void addPlayer(Player player) {
        playersByUuid.put(player.getUniqueId(), player);
        playersByName.put(player.getName().toLowerCase(), player);
        updateCachedCollection();

        plugin.getLogger().fine("[PlayerCache] Joueur ajouté: " + player.getName());
    }

    /**
     * Retire un joueur du cache
     */
    private void removePlayer(Player player) {
        playersByUuid.remove(player.getUniqueId());
        playersByName.remove(player.getName().toLowerCase());
        updateCachedCollection();

        plugin.getLogger().fine("[PlayerCache] Joueur retiré: " + player.getName());
    }

    /**
     * Met à jour la collection cachée (thread-safe)
     */
    private void updateCachedCollection() {
        cachedOnlinePlayers = new ArrayList<>(playersByUuid.values());
    }

    // === API PUBLIQUE ===

    /**
     * Récupère un joueur par UUID (O(1))
     *
     * @param uuid UUID du joueur
     * @return Le joueur s'il est en ligne, null sinon
     */
    public Player getOnlinePlayer(UUID uuid) {
        return playersByUuid.get(uuid);
    }

    /**
     * Récupère un joueur par nom (O(1))
     *
     * @param name Nom du joueur (insensible à la casse)
     * @return Le joueur s'il est en ligne, null sinon
     */
    public Player getOnlinePlayer(String name) {
        return playersByName.get(name.toLowerCase());
    }

    /**
     * Vérifie si un joueur est en ligne (O(1))
     *
     * @param uuid UUID du joueur
     * @return true si le joueur est en ligne
     */
    public boolean isOnline(UUID uuid) {
        return playersByUuid.containsKey(uuid);
    }

    /**
     * Vérifie si un joueur est en ligne (O(1))
     *
     * @param name Nom du joueur (insensible à la casse)
     * @return true si le joueur est en ligne
     */
    public boolean isOnline(String name) {
        return playersByName.containsKey(name.toLowerCase());
    }

    /**
     * Retourne tous les joueurs en ligne (retourne une copie pré-construite)
     *
     * @return Collection des joueurs en ligne (lecture seule)
     */
    public Collection<Player> getOnlinePlayers() {
        return cachedOnlinePlayers;
    }

    /**
     * Retourne le nombre de joueurs en ligne (O(1))
     *
     * @return Nombre de joueurs en ligne
     */
    public int getOnlineCount() {
        return playersByUuid.size();
    }

    /**
     * Récupère tous les UUIDs des joueurs en ligne
     *
     * @return Set des UUIDs (copie défensive)
     */
    public Set<UUID> getOnlineUuids() {
        return new HashSet<>(playersByUuid.keySet());
    }

    /**
     * Récupère tous les noms des joueurs en ligne
     *
     * @return Set des noms (copie défensive)
     */
    public Set<String> getOnlineNames() {
        return new HashSet<>(playersByName.keySet());
    }

    /**
     * Filtre les joueurs en ligne avec une permission
     *
     * @param permission Permission à vérifier
     * @return Liste des joueurs ayant la permission
     */
    public List<Player> getOnlinePlayersWithPermission(String permission) {
        List<Player> result = new ArrayList<>();
        for (Player player : cachedOnlinePlayers) {
            if (player.hasPermission(permission)) {
                result.add(player);
            }
        }
        return result;
    }

    /**
     * Vide le cache (utile pour le reload)
     */
    public void clear() {
        playersByUuid.clear();
        playersByName.clear();
        updateCachedCollection();
        plugin.getLogger().info("[PlayerCache] Cache vidé");
    }

    /**
     * Reconstruit entièrement le cache
     */
    public void rebuild() {
        clear();
        initializeCache();
        plugin.getLogger().info("[PlayerCache] Cache reconstruit avec " + playersByUuid.size() + " joueurs");
    }

    // === LISTENERS ===

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        addPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        removePlayer(event.getPlayer());
    }

    // === STATISTIQUES ===

    /**
     * Retourne des statistiques sur le cache
     */
    public String getStats() {
        return String.format(
            "PlayerCache Stats: %d joueurs en ligne, %d UUIDs indexés, %d noms indexés",
            cachedOnlinePlayers.size(),
            playersByUuid.size(),
            playersByName.size()
        );
    }
}
