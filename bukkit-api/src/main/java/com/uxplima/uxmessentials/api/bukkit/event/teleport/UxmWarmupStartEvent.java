package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.api.view.UxmTeleportKind;
import org.jspecify.annotations.NullMarked;

/** A teleport warmup began. The player must stand still for its duration or it is cancelled. */
@NullMarked
public final class UxmWarmupStartEvent extends UxmTeleportEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmTeleportKind kind;
    private final UxmLocation origin;
    private final Duration duration;

    public UxmWarmupStartEvent(
            UUID playerId, String playerName, UxmTeleportKind kind, UxmLocation origin, Duration duration) {
        super(playerId, playerName);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.duration = Objects.requireNonNull(duration, "duration");
    }

    /** Which kind of teleport is waiting on this warmup. */
    public UxmTeleportKind getKind() {
        return kind;
    }

    /** Where the player must stay. */
    public UxmLocation getOrigin() {
        return origin;
    }

    /** How long they must stay there. */
    public Duration getDuration() {
        return duration;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
