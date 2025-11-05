package com.gravityyfh.roleplaycity.town.data;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

/**
 * Classe pour tracker les blocs placés par les locataires
 * Permet de vérifier si un locataire peut casser un bloc
 */
public class RenterBlockTracker {

    // Map: UUID du locataire -> Set de positions de blocs placés
    private final Map<UUID, Set<BlockPosition>> renterBlocks;

    public RenterBlockTracker() {
        this.renterBlocks = new HashMap<>();
    }

    /**
     * Enregistre qu'un locataire a placé un bloc
     */
    public void addBlock(UUID renterUuid, Location location) {
        renterBlocks.computeIfAbsent(renterUuid, k -> new HashSet<>())
                .add(new BlockPosition(location));
    }

    /**
     * Retire un bloc de la liste (quand le locataire le casse)
     */
    public void removeBlock(UUID renterUuid, Location location) {
        Set<BlockPosition> blocks = renterBlocks.get(renterUuid);
        if (blocks != null) {
            blocks.remove(new BlockPosition(location));
            // Nettoyer si vide
            if (blocks.isEmpty()) {
                renterBlocks.remove(renterUuid);
            }
        }
    }

    /**
     * Vérifie si un locataire a placé ce bloc (donc peut le casser)
     */
    public boolean canRenterBreak(UUID renterUuid, Location location) {
        Set<BlockPosition> blocks = renterBlocks.get(renterUuid);
        if (blocks == null) {
            return false;
        }
        return blocks.contains(new BlockPosition(location));
    }

    /**
     * Nettoie tous les blocs d'un locataire (quand la location expire)
     */
    public void clearRenter(UUID renterUuid) {
        renterBlocks.remove(renterUuid);
    }

    /**
     * Nettoie tous les blocs trackés
     */
    public void clearAll() {
        renterBlocks.clear();
    }

    /**
     * Retourne le nombre total de blocs trackés pour un locataire
     */
    public int getRenterBlockCount(UUID renterUuid) {
        Set<BlockPosition> blocks = renterBlocks.get(renterUuid);
        return blocks != null ? blocks.size() : 0;
    }

    /**
     * Retourne tous les blocs pour la sauvegarde
     */
    public Map<UUID, Set<BlockPosition>> getAllBlocks() {
        return new HashMap<>(renterBlocks);
    }

    /**
     * Charge des blocs depuis la sauvegarde
     */
    public void loadBlocks(Map<UUID, Set<BlockPosition>> blocks) {
        this.renterBlocks.clear();
        if (blocks != null) {
            this.renterBlocks.putAll(blocks);
        }
    }

    /**
     * Classe interne pour représenter une position de bloc
     */
    public static class BlockPosition {
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;

        public BlockPosition(Location location) {
            this.worldName = location.getWorld().getName();
            this.x = location.getBlockX();
            this.y = location.getBlockY();
            this.z = location.getBlockZ();
        }

        public BlockPosition(String worldName, int x, int y, int z) {
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public String getWorldName() {
            return worldName;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }

        /**
         * Convertit en format String pour sauvegarde YAML
         * Format: "worldName:x:y:z"
         */
        public String serialize() {
            return worldName + ":" + x + ":" + y + ":" + z;
        }

        /**
         * Crée depuis un String sauvegardé
         */
        public static BlockPosition deserialize(String data) {
            String[] parts = data.split(":");
            if (parts.length != 4) {
                return null;
            }
            try {
                return new BlockPosition(
                    parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3])
                );
            } catch (NumberFormatException e) {
                return null;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BlockPosition that = (BlockPosition) o;
            return x == that.x && y == that.y && z == that.z &&
                   Objects.equals(worldName, that.worldName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(worldName, x, y, z);
        }

        @Override
        public String toString() {
            return worldName + ":" + x + ":" + y + ":" + z;
        }
    }
}
