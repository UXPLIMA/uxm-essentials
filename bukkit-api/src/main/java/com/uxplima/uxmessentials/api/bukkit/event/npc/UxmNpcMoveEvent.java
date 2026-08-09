package com.uxplima.uxmessentials.api.bukkit.event.npc;

import java.util.Objects;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import org.jspecify.annotations.NullMarked;

/**
 * An NPC was re-anchored to a new position.
 *
 * <p>This is the recorded home position changing, not the per-tick movement of a walking NPC, so it fires once per
 * move rather than continuously.
 */
@NullMarked
public final class UxmNpcMoveEvent extends UxmNpcEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmLocation location;

    public UxmNpcMoveEvent(String npcName, UxmLocation location) {
        super(npcName, null, null);
        this.location = Objects.requireNonNull(location, "location");
    }

    /** Where the NPC now stands. */
    public UxmLocation getLocation() {
        return location;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
