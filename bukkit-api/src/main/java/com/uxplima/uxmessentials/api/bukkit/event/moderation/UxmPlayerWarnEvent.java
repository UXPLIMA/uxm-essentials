package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmIssuer;
import org.jspecify.annotations.NullMarked;

/**
 * A player was warned.
 *
 * <p>The running total is carried because warning escalation is driven by it: a listener wanting to act on the third
 * warning does not have to count them itself.
 */
@NullMarked
public final class UxmPlayerWarnEvent extends UxmModerationEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UxmIssuer issuer;
    private final Optional<String> reason;
    private final Optional<Instant> expiresAt;
    private final int totalWarnings;

    public UxmPlayerWarnEvent(
            UUID targetId,
            String targetName,
            UxmIssuer issuer,
            Optional<String> reason,
            Optional<Instant> expiresAt,
            int totalWarnings,
            Instant at) {
        super(targetId, targetName, at);
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.totalWarnings = totalWarnings;
    }

    /** Who warned them. */
    public UxmIssuer getIssuer() {
        return issuer;
    }

    /** The reason given, if one was. */
    public Optional<String> getReason() {
        return reason;
    }

    /** When this warning stops counting, or empty when it never does. */
    public Optional<Instant> getExpiresAt() {
        return expiresAt;
    }

    /** How many warnings the player now has, this one included. */
    public int getTotalWarnings() {
        return totalWarnings;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
