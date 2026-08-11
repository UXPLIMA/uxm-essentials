package com.uxplima.uxmessentials.api.bukkit.event.security;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * An account ran out of attempts, and no surface will take another proof from it for a while.
 *
 * <p>{@link #isBanned()} says whether the lockout was also written to the server's own ban list. When it is false
 * the lockout is the in-memory window alone, and a restart forgets it.
 */
@NullMarked
public final class UxmSecurityLockoutEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Duration lockout;
    private final boolean banned;

    public UxmSecurityLockoutEvent(UUID playerId, String playerName, Duration lockout, boolean banned) {
        super(playerId, playerName);
        this.lockout = Objects.requireNonNull(lockout, "lockout");
        this.banned = banned;
    }

    /** How long the lockout lasts. */
    public Duration getLockout() {
        return lockout;
    }

    /** Whether it was recorded as an ordinary tempban rather than kept only in memory. */
    public boolean isBanned() {
        return banned;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
