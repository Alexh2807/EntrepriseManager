package com.gravityyfh.roleplaycity.town.data;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Représente les coordonnées d'un chunk de manière immuable
 * Utilisé comme clé pour identifier les chunks claimés
 */
public class ChunkCoordinate {
    private final String worldName;
    private final int x;
    private final int z;

    public ChunkCoordinate(String worldName, int x, int z) {
        this.worldName = worldName;
        this.x = x;
        this.z = z;
    }

    public ChunkCoordinate(Chunk chunk) {
        this(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public ChunkCoordinate(Location location) {
        this(location.getWorld().getName(),
             location.getBlockX() >> 4,
             location.getBlockZ() >> 4);
    }

    public static ChunkCoordinate fromLocation(Location location) {
        return new ChunkCoordinate(location);
    }

    public static ChunkCoordinate fromChunk(Chunk chunk) {
        return new ChunkCoordinate(chunk);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String toKey() {
        return worldName + ":" + x + ":" + z;
    }

    public static ChunkCoordinate fromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid chunk coordinate key: " + key);
        }
        return new ChunkCoordinate(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    public boolean isAdjacentTo(ChunkCoordinate other) {
        if (!this.worldName.equals(other.worldName)) {
            return false;
        }
        int dx = Math.abs(this.x - other.x);
        int dz = Math.abs(this.z - other.z);
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkCoordinate that = (ChunkCoordinate) o;
        return x == that.x && z == that.z && Objects.equals(worldName, that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, z);
    }

    @Override
    public String toString() {
        return "ChunkCoordinate{" + worldName + ", x=" + x + ", z=" + z + '}';
    }
}
