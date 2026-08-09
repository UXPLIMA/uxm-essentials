package com.uxplima.uxmessentials.api.bukkit.event.npc;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import org.jspecify.annotations.NullMarked;

/** An NPC was created. */
@NullMarked
public final class UxmNpcCreateEvent extends UxmNpcEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmLocation location;

    public UxmNpcCreateEvent(String npcName, UUID actorId, String actorName, UxmLocation location) {
        super(npcName, actorId, actorName);
        this.location = Objects.requireNonNull(location, "location");
    }

    /** Where the NPC stands. */
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
