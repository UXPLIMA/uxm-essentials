package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmIssuer;
import org.jspecify.annotations.NullMarked;

/**
 * A player was banned, by {@code /tempban}, {@code /ban} or anything that issues either. A permanent ban fires this
 * event too: {@link #isPermanent()} tells it apart, and its {@link #getUntil()} is then a far-future stand-in, not a
 * moment the ban lifts.
 */
@NullMarked
public final class UxmPlayerTempbanEvent extends UxmModerationEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmIssuer issuer;
    private final Optional<String> reason;
    private final Instant until;
    private final boolean permanent;

    /** A timed ban lifting at {@code until}. */
    public UxmPlayerTempbanEvent(
            UUID targetId, String targetName, UxmIssuer issuer, Optional<String> reason, Instant until, Instant at) {
        this(targetId, targetName, issuer, reason, until, false, at);
    }

    /**
     * A ban lifting at {@code until}, or a permanent one when {@code permanent} is true, in which case {@code until} is
     * the stored far-future expiry.
     */
    public UxmPlayerTempbanEvent(
            UUID targetId,
            String targetName,
            UxmIssuer issuer,
            Optional<String> reason,
            Instant until,
            boolean permanent,
            Instant at) {
        super(targetId, targetName, at);
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.until = Objects.requireNonNull(until, "until");
        this.permanent = permanent;
    }

    /** Who banned them. */
    public UxmIssuer getIssuer() {
        return issuer;
    }

    /** The reason given, if one was. */
    public Optional<String> getReason() {
        return reason;
    }

    /**
     * When the ban lifts. For a permanent ban ({@link #isPermanent()}) this is the far-future instant the ban is stored
     * with, not a real end; check {@link #isPermanent()} before showing it.
     */
    public Instant getUntil() {
        return until;
    }

    /** True when the ban never lifts on its own: a {@code /ban} that no duration cap shortened. */
    public boolean isPermanent() {
        return permanent;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
