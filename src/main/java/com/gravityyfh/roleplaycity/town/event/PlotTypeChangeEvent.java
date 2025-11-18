package com.gravityyfh.roleplaycity.town.event;

import com.gravityyfh.roleplaycity.town.data.Plot;
import com.gravityyfh.roleplaycity.town.data.PlotType;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Événement appelé quand le type d'un terrain change
 */
public class PlotTypeChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Plot plot;
    private final PlotType oldType;
    private final PlotType newType;

    public PlotTypeChangeEvent(Plot plot, PlotType oldType, PlotType newType) {
        this.plot = plot;
        this.oldType = oldType;
        this.newType = newType;
    }

    public Plot getPlot() {
        return plot;
    }

    public PlotType getOldType() {
        return oldType;
    }

    public PlotType getNewType() {
        return newType;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
