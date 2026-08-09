package com.uxplima.uxmessentials.api.bukkit.event.playerstate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmSpeedKind;
import org.jspecify.annotations.NullMarked;

/** A player's walking or flight speed was set. */
@NullMarked
public final class UxmPlayerSpeedChangeEvent extends UxmPlayerStateEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmSpeedKind kind;
    private final double scale;

    public UxmPlayerSpeedChangeEvent(
            UUID subjectId,
            String subjectName,
            UUID actorId,
            String actorName,
            UxmSpeedKind kind,
            double scale,
            Instant at) {
        super(subjectId, subjectName, actorId, actorName, at);
        this.kind = Objects.requireNonNull(kind, "kind");
        this.scale = scale;
    }

    /** Which of the two speeds was set. */
    public UxmSpeedKind getKind() {
        return kind;
    }

    /** The multiplier it was set to, where one is the vanilla default. */
    public double getScale() {
        return scale;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
