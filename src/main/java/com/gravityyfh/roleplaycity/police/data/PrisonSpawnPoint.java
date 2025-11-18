package com.gravityyfh.roleplaycity.police.data;

import org.bukkit.Location;

/**
 * Point de spawn de prison pour un claim COMMISSARIAT
 */
public class PrisonSpawnPoint {

    private final String townName;
    private final String plotIdentifier;
    private final Location spawnLocation;

    public PrisonSpawnPoint(String townName, String plotIdentifier, Location spawnLocation) {
        this.townName = townName;
        this.plotIdentifier = plotIdentifier;
        this.spawnLocation = spawnLocation;
    }

    /**
     * Crée une clé unique pour identifier ce spawn
     */
    public String getKey() {
        return townName + ":" + plotIdentifier;
    }
}
