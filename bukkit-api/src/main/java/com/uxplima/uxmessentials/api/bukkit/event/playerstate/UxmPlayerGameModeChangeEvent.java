package com.uxplima.uxmessentials.api.bukkit.event.playerstate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.event.HandlerList;

import org.jspecify.annotations.NullMarked;

/** A player's game mode was changed through uxmEssentials. */
@NullMarked
public final class UxmPlayerGameModeChangeEvent extends UxmPlayerStateEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final GameMode mode;

    public UxmPlayerGameModeChangeEvent(
            UUID subjectId, String subjectName, UUID actorId, String actorName, GameMode mode, Instant at) {
        super(subjectId, subjectName, actorId, actorName, at);
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    /** The mode they are now in. */
    public GameMode getMode() {
        return mode;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
