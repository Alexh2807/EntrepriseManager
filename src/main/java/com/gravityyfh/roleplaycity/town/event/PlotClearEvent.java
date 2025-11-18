package com.gravityyfh.roleplaycity.town.event;

import com.gravityyfh.roleplaycity.town.data.Plot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Événement appelé quand un terrain est "nettoyé" (propriétaire et locataire supprimés)
 */
public class PlotClearEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Plot plot;
    private final UUID oldOwnerUuid;
    private final String reason;

    public PlotClearEvent(Plot plot, UUID oldOwnerUuid, String reason) {
        this.plot = plot;
        this.oldOwnerUuid = oldOwnerUuid;
        this.reason = reason;
    }

    public Plot getPlot() {
        return plot;
    }

    public UUID getOldOwnerUuid() {
        return oldOwnerUuid;
    }

    public String getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
