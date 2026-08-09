package com.uxplima.uxmessentials.api.bukkit.event.moderation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.view.UxmIssuer;
import org.jspecify.annotations.NullMarked;

/** A player was jailed. An empty expiry means they stay until somebody lets them out. */
@NullMarked
public final class UxmPlayerJailEvent extends UxmModerationEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String jail;
    private final UxmIssuer issuer;
    private final Optional<String> reason;
    private final Optional<Instant> until;

    public UxmPlayerJailEvent(
            UUID targetId,
            String targetName,
            String jail,
            UxmIssuer issuer,
            Optional<String> reason,
            Optional<Instant> until,
            Instant at) {
        super(targetId, targetName, at);
        this.jail = Objects.requireNonNull(jail, "jail");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.reason = Objects.requireNonNull(reason, "reason");
        this.until = Objects.requireNonNull(until, "until");
    }

    /** Which jail they were put in. */
    public String getJail() {
        return jail;
    }

    /** Who jailed them. */
    public UxmIssuer getIssuer() {
        return issuer;
    }

    /** The reason given, if one was. */
    public Optional<String> getReason() {
        return reason;
    }

    /** When the sentence ends, or empty when it is open-ended. */
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
