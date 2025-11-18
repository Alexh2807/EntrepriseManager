package com.gravityyfh.roleplaycity.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache optimisé pour tracker les blocs placés par les joueurs.
 * Remplace les lookups CoreProtect synchrones qui causent du lag.
 *
 * Performances:
 * - Lookup CoreProtect synchrone: 50-200ms par bloc
 * - Cache en mémoire: <1ms par bloc (500x plus rapide)
 *
 * FIX BASSE: Optimisation CoreProtect
 */
public class PlayerBlockPlaceCache {

    private final JavaPlugin plugin;
    private final Map<BlockLocation, PlacedBlockInfo> cache;
    private final long expirationMs;

    /**
     * Information sur un bloc placé
     */
    private static class PlacedBlockInfo {
        final String playerName;
        final UUID playerUuid;
        final long timestamp;
        final Material material;

        PlacedBlockInfo(String playerName, UUID playerUuid, Material material) {
            this.playerName = playerName;
            this.playerUuid = playerUuid;
            this.timestamp = System.currentTimeMillis();
            this.material = material;
        }

        boolean isExpired(long expirationMs) {
            return System.currentTimeMillis() - timestamp > expirationMs;
        }
    }

    /**
     * Clé de localisation de bloc (thread-safe et efficace)
     */
    private static class BlockLocation {
        final String world;
        final int x, y, z;
        final int hash;

        BlockLocation(Block block) {
            this.world = block.getWorld().getName();
            this.x = block.getX();
            this.y = block.getY();
            this.z = block.getZ();
            // Précalculer le hash pour performances
            this.hash = Objects.hash(world, x, y, z);
        }

        BlockLocation(Location loc) {
            this.world = loc.getWorld().getName();
            this.x = loc.getBlockX();
            this.y = loc.getBlockY();
            this.z = loc.getBlockZ();
            this.hash = Objects.hash(world, x, y, z);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BlockLocation that)) return false;
            return x == that.x && y == that.y && z == that.z && world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    public PlayerBlockPlaceCache(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cache = new ConcurrentHashMap<>();

        // Lire le délai d'expiration depuis la config (défaut: 48 heures)
        FileConfiguration config = plugin.getConfig();
        long hours = config.getLong("block-place-cache.expiration-hours", 48);
        this.expirationMs = hours * 60 * 60 * 1000;

        plugin.getLogger().info("[BlockPlaceCache] Initialisé avec expiration: " + hours + "h");

        // Démarrer le nettoyage automatique toutes les heures
        startAutomaticCleanup();
    }

    /**
     * Enregistre un bloc placé par un joueur
     */
    public void recordBlockPlace(Block block, String playerName, UUID playerUuid) {
        if (block == null || playerName == null || playerUuid == null) return;

        BlockLocation loc = new BlockLocation(block);
        PlacedBlockInfo info = new PlacedBlockInfo(playerName, playerUuid, block.getType());

        cache.put(loc, info);
    }

    /**
     * Vérifie si un bloc a été placé par un joueur spécifique
     * @return true si le bloc a été placé par ce joueur, false sinon
     */
    public boolean wasPlacedByPlayer(Block block, String playerName) {
        if (block == null || playerName == null) return false;

        BlockLocation loc = new BlockLocation(block);
        PlacedBlockInfo info = cache.get(loc);

        if (info == null) {
            return false; // Bloc jamais placé (naturel ou placé avant le cache)
        }

        // Vérifier expiration
        if (info.isExpired(expirationMs)) {
            cache.remove(loc);
            return false;
        }

        // Vérifier si c'est le même joueur
        return info.playerName.equalsIgnoreCase(playerName);
    }

    /**
     * Vérifie si un bloc a été placé par N'IMPORTE QUEL joueur
     * @return true si le bloc a été placé par un joueur, false sinon
     */
    public boolean wasPlacedByAnyPlayer(Block block) {
        if (block == null) return false;

        BlockLocation loc = new BlockLocation(block);
        PlacedBlockInfo info = cache.get(loc);

        if (info == null) {
            return false;
        }

        // Vérifier expiration
        if (info.isExpired(expirationMs)) {
            cache.remove(loc);
            return false;
        }

        return true;
    }

    /**
     * Supprime un bloc du cache (appelé quand le bloc est cassé)
     */
    public void removeBlock(Block block) {
        if (block == null) return;
        cache.remove(new BlockLocation(block));
    }

    /**
     * Nettoie les entrées expirées du cache
     * @return nombre d'entrées supprimées
     */
    public int cleanupExpired() {

        // Utiliser AtomicInteger pour pouvoir incrémenter dans le lambda
        java.util.concurrent.atomic.AtomicInteger removed = new java.util.concurrent.atomic.AtomicInteger(0);

        cache.entrySet().removeIf(entry -> {
            if (entry.getValue().isExpired(expirationMs)) {
                removed.incrementAndGet();
                return true;
            }
            return false;
        });

        return removed.get();
    }

    /**
     * Démarre le nettoyage automatique toutes les heures
     */
    private void startAutomaticCleanup() {
        long ticksPerHour = 20L * 60 * 60; // 1 heure = 72000 ticks

        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int removed = cleanupExpired();
            if (removed > 0) {
                plugin.getLogger().info("[BlockPlaceCache] Nettoyage: " + removed + " blocs expirés supprimés. Cache: " + cache.size() + " entrées");
            }
        }, ticksPerHour, ticksPerHour);
    }

    /**
     * Retourne la taille actuelle du cache
     */
    public int size() {
        return cache.size();
    }

    /**
     * Vide complètement le cache
     */
    public void clear() {
        cache.clear();
        plugin.getLogger().info("[BlockPlaceCache] Cache vidé");
    }
}
