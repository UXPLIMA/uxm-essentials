package com.uxplima.uxmessentials.api.bukkit.event.npc;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** An NPC was deleted. */
@NullMarked
public final class UxmNpcDeleteEvent extends UxmNpcEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public UxmNpcDeleteEvent(String npcName, UUID actorId, String actorName) {
        super(npcName, actorId, actorName);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
