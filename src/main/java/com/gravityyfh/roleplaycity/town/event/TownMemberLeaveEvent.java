package com.gravityyfh.roleplaycity.town.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Événement appelé quand un membre quitte ou est expulsé d'une ville
 */
public class TownMemberLeaveEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String townName;
    private final UUID playerUuid;
    private final String playerName;

    public TownMemberLeaveEvent(String townName, UUID playerUuid, String playerName) {
        this.townName = townName;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
    }

    public String getTownName() {
        return townName;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
