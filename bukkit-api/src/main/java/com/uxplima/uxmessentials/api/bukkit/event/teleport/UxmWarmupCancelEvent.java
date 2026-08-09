package com.uxplima.uxmessentials.api.bukkit.event.teleport;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmTeleportKind;
import com.uxplima.uxmessentials.api.view.UxmWarmupCancelReason;
import org.jspecify.annotations.NullMarked;

/** A teleport warmup was cut short. The teleport it was waiting on will not happen. */
@NullMarked
public final class UxmWarmupCancelEvent extends UxmTeleportEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmTeleportKind kind;
    private final UxmWarmupCancelReason reason;

    public UxmWarmupCancelEvent(UUID playerId, String playerName, UxmTeleportKind kind, UxmWarmupCancelReason reason) {
        super(playerId, playerName);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** Which kind of teleport was waiting on the warmup. */
    public UxmTeleportKind getKind() {
        return kind;
    }

    /** What cut it short. */
    public UxmWarmupCancelReason getReason() {
        return reason;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
