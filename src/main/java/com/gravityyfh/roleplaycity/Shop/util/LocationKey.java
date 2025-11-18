package com.gravityyfh.roleplaycity.shop.util;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

/**
 * Clé de localisation pour HashMap
 * Compare uniquement world, x, y, z (ignore yaw/pitch)
 *
 * Résout le problème de comparaison des Locations après désérialisation
 * où yaw/pitch peuvent varier légèrement
 */
public class LocationKey {
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    public LocationKey(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location cannot be null");
        }
        if (location.getWorld() == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }

        this.worldName = location.getWorld().getName();
        this.x = location.getBlockX();
        this.y = location.getBlockY();
        this.z = location.getBlockZ();
    }

    public LocationKey(String worldName, int x, int y, int z) {
        this.worldName = Objects.requireNonNull(worldName, "worldName cannot be null");
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocationKey that = (LocationKey) o;
        return x == that.x &&
               y == that.y &&
               z == that.z &&
               worldName.equals(that.worldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldName, x, y, z);
    }

    @Override
    public String toString() {
        return worldName + "(" + x + "," + y + "," + z + ")";
    }
}
