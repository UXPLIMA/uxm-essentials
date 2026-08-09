package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmIssuer;
import org.jspecify.annotations.NullMarked;

/** A player was muted. An empty expiry means the mute is permanent. */
@NullMarked
public final class UxmPlayerMuteEvent extends UxmModerationEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmIssuer issuer;
    private final Optional<String> reason;
    private final Optional<Instant> until;

    public UxmPlayerMuteEvent(
            UUID targetId,
            String targetName,
            UxmIssuer issuer,
            Optional<String> reason,
            Optional<Instant> until,
            Instant at) {
        super(targetId, targetName, at);
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.until = Objects.requireNonNull(until, "until");
    }

    /** Who muted them. */
    public UxmIssuer getIssuer() {
        return issuer;
    }

    /** The reason given, if one was. */
    public Optional<String> getReason() {
        return reason;
    }

    /** When the mute lifts, or empty when it is permanent. */
    public Optional<Instant> getUntil() {
        return until;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
