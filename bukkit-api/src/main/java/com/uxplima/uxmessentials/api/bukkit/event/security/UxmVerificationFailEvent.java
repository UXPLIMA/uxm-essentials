package com.uxplima.uxmessentials.api.bukkit.event.security;

import java.util.UUID;

import org.bukkit.event.HandlerList;

import com.uxplima.uxmessentials.api.bukkit.event.UxmPlayerEvent;
import org.jspecify.annotations.NullMarked;

/**
 * A submitted value proved nothing, and the account still has tries left.
 *
 * <p>The attempt that spends the last one fires {@link UxmSecurityLockoutEvent} instead, so a submission never
 * fires both. Which factor was presented is not carried: the verification does not learn it either.
 */
@NullMarked
public final class UxmVerificationFailEvent extends UxmPlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int remainingAttempts;

    public UxmVerificationFailEvent(UUID playerId, String playerName, int remainingAttempts) {
        super(playerId, playerName);
        this.remainingAttempts = remainingAttempts;
    }

    /** How many tries are left before the account is locked out. */
    public int getRemainingAttempts() {
        return remainingAttempts;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
