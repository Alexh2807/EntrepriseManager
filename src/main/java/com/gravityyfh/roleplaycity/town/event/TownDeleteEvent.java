package com.gravityyfh.roleplaycity.town.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Événement appelé quand une ville est supprimée
 */
public class TownDeleteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String townName;

    public TownDeleteEvent(String townName) {
        this.townName = townName;
    }

    public String getTownName() {
        return townName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
