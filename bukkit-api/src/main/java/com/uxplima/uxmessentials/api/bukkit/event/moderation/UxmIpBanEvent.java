package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmEvent;
import com.uxplima.uxmessentials.api.view.UxmIssuer;
import org.jspecify.annotations.NullMarked;

/**
 * An address was banned.
 *
 * <p>An address is not an account, which is why this is not a player event: the ban may have been typed as a raw
 * address with no player behind it at all. When it was applied through a player, their id is carried.
 */
@NullMarked
public final class UxmIpBanEvent extends UxmEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String ip;
    private final Optional<UUID> target;
    private final Optional<Instant> until;
    private final Optional<String> reason;
    private final UxmIssuer issuer;
    private final Instant issuedAt;

    public UxmIpBanEvent(
            String ip,
            Optional<UUID> target,
            Optional<Instant> until,
            Optional<String> reason,
            UxmIssuer issuer,
            Instant issuedAt) {
        this.ip = Objects.requireNonNull(ip, "ip");
        this.target = Objects.requireNonNull(target, "target");
        this.until = Objects.requireNonNull(until, "until");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
    }

    /** The banned address. */
    public String getIp() {
        return ip;
    }

    /** The account the ban was applied through, when it was applied through one. */
    public Optional<UUID> getTarget() {
        return target;
    }

    /** When the ban lifts, or empty when it is permanent. */
    public Optional<Instant> getUntil() {
        return until;
    }

    /** The reason given, if one was. */
    public Optional<String> getReason() {
        return reason;
    }

    /** Who banned it. */
    public UxmIssuer getIssuer() {
        return issuer;
    }

    /** When it was banned. */
    public Instant getIssuedAt() {
        return issuedAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
