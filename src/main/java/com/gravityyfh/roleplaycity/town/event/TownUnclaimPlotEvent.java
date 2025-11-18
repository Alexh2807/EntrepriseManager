package com.gravityyfh.roleplaycity.town.event;

import com.gravityyfh.roleplaycity.town.data.Plot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Événement appelé quand un terrain est unclaimed par la ville
 */
public class TownUnclaimPlotEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String townName;
    private final Plot plot;
    private final String worldName;
    private final int chunkX;
    private final int chunkZ;

    public TownUnclaimPlotEvent(String townName, Plot plot, String worldName, int chunkX, int chunkZ) {
        this.townName = townName;
        this.plot = plot;
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public String getTownName() {
        return townName;
    }

    public Plot getPlot() {
        return plot;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
