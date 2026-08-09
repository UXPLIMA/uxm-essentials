package com.uxplima.uxmessentials.api.bukkit.event.communication;

import java.time.Instant;
import java.util.Objects;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import org.jspecify.annotations.NullMarked;

/** The automatic announcer re-read its lines. Server-wide, so no player is its subject. */
@NullMarked
public final class UxmAnnouncerReloadEvent extends UxmEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int lineCount;
    private final Instant at;

    public UxmAnnouncerReloadEvent(int lineCount, Instant at) {
        this.lineCount = lineCount;
        this.at = Objects.requireNonNull(at, "at");
    }

    /** How many lines it now has. */
    public int getLineCount() {
        return lineCount;
    }

    /** When it reloaded. */
    public Instant getAt() {
        return at;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
