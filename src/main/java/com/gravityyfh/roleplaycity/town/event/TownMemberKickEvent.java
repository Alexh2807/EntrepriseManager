package com.gravityyfh.roleplaycity.town.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

/**
 * Événement appelé quand un membre est EXPULSÉ d'une ville (kick)
 * Distinct de TownMemberLeaveEvent qui gère aussi les départs volontaires
 */
public class TownMemberKickEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final String townName;
    private final UUID kickedPlayerUuid;
    private final String kickedPlayerName;
    private final UUID kickerUuid;
    private final String kickerName;

    public TownMemberKickEvent(String townName, UUID kickedPlayerUuid, String kickedPlayerName,
                               UUID kickerUuid, String kickerName) {
        this.townName = townName;
        this.kickedPlayerUuid = kickedPlayerUuid;
        this.kickedPlayerName = kickedPlayerName;
        this.kickerUuid = kickerUuid;
        this.kickerName = kickerName;
    }

    public String getTownName() {
        return townName;
    }

    public UUID getKickedPlayerUuid() {
        return kickedPlayerUuid;
    }

    public String getKickedPlayerName() {
        return kickedPlayerName;
    }

    public UUID getKickerUuid() {
        return kickerUuid;
    }

    public String getKickerName() {
        return kickerName;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
