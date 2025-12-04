package com.gravityyfh.roleplaycity.mdt.reset;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Tracker des blocs placés et cassés pendant une partie MDT Rush
 * Permet de restaurer la map à son état original après chaque partie
 *
 * Utilise des clés String "world:x:y:z" pour éviter les problèmes de comparaison de Location
 */
public class BlockTracker {

    // Map: "world:x:y:z" -> BlockData original (avant modification)
    private final Map<String, BlockData> originalBlocks = new HashMap<>();

    // Set des coordonnées des blocs placés par les joueurs: "world:x:y:z"
    private final Set<String> playerPlacedBlocks = new HashSet<>();

    /**
     * Crée une clé unique pour une position
     */
    private String toKey(Location location) {
        return location.getWorld().getName() + ":" +
               location.getBlockX() + ":" +
               location.getBlockY() + ":" +
               location.getBlockZ();
    }

    /**
     * Crée une clé unique pour des coordonnées
     */
    private String toKey(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    /**
     * Parse une clé en Location
     */
    private Location fromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) return null;

        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;

        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Appelé quand un joueur place un bloc
     */
    public void onBlockPlace(Location location) {
        String key = toKey(location);

        // Si c'est la première modification à cette position, sauvegarder l'état original
        if (!originalBlocks.containsKey(key)) {
            // L'état original était AIR (le bloc n'existait pas avant)
            originalBlocks.put(key, null);
        }

        playerPlacedBlocks.add(key);
    }

    /**
     * Appelé quand un joueur casse un bloc
     */
    public void onBlockBreak(Location location, BlockData originalData) {
        String key = toKey(location);

        // Si c'est la première modification à cette position, sauvegarder l'état original
        if (!originalBlocks.containsKey(key)) {
            originalBlocks.put(key, originalData.clone());
        }

        // Retirer des blocs placés par joueurs (si c'était un bloc placé)
        playerPlacedBlocks.remove(key);
    }

    /**
     * Appelé quand un bloc est détruit par une explosion
     */
    public void onBlockExplode(Location location, BlockData originalData) {
        String key = toKey(location);

        // Si c'est la première modification, sauvegarder l'état original
        if (!originalBlocks.containsKey(key)) {
            originalBlocks.put(key, originalData.clone());
        }

        playerPlacedBlocks.remove(key);
    }

    /**
     * Vérifie si un bloc a été placé par un joueur
     */
    public boolean isPlayerPlacedBlock(Location location) {
        return playerPlacedBlocks.contains(toKey(location));
    }

    /**
     * Retourne tous les blocs originaux modifiés sous forme de Map<Location, BlockData>
     */
    public Map<Location, BlockData> getOriginalBlocks() {
        Map<Location, BlockData> result = new HashMap<>();
        for (Map.Entry<String, BlockData> entry : originalBlocks.entrySet()) {
            Location loc = fromKey(entry.getKey());
            if (loc != null) {
                result.put(loc, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Retourne tous les blocs placés par les joueurs sous forme de Set<Location>
     */
    public Set<Location> getPlayerPlacedBlocks() {
        Set<Location> result = new HashSet<>();
        for (String key : playerPlacedBlocks) {
            Location loc = fromKey(key);
            if (loc != null) {
                result.add(loc);
            }
        }
        return result;
    }

    /**
     * Retourne le nombre de blocs placés par les joueurs
     */
    public int getPlayerPlacedBlockCount() {
        return playerPlacedBlocks.size();
    }

    /**
     * Réinitialise le tracker (à appeler après le reset de la map)
     */
    public void clear() {
        originalBlocks.clear();
        playerPlacedBlocks.clear();
    }

    /**
     * Retourne le nombre de blocs modifiés
     */
    public int getModifiedBlockCount() {
        return originalBlocks.size();
    }
}
