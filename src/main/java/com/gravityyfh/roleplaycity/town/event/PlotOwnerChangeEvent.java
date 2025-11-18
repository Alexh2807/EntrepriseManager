package com.gravityyfh.roleplaycity.town.event;

import com.gravityyfh.roleplaycity.town.data.Plot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Événement appelé quand le propriétaire d'un terrain change
 */
public class PlotOwnerChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Plot plot;
    private final UUID oldOwnerUuid;
    private final UUID newOwnerUuid;
    private final String newOwnerName;

    public PlotOwnerChangeEvent(Plot plot, UUID oldOwnerUuid, UUID newOwnerUuid, String newOwnerName) {
        this.plot = plot;
        this.oldOwnerUuid = oldOwnerUuid;
        this.newOwnerUuid = newOwnerUuid;
        this.newOwnerName = newOwnerName;
    }

    public Plot getPlot() {
        return plot;
    }

    public UUID getOldOwnerUuid() {
        return oldOwnerUuid;
    }

    public UUID getNewOwnerUuid() {
        return newOwnerUuid;
    }

    public String getNewOwnerName() {
        return newOwnerName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
