package com.uxplima.uxmessentials.security.adapter.outbound.api;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.api.action.UxmSecurityActions;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.ForceReverification;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncActions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The two published security writes, over the same use case and limiter the {@code /2fa} command drives.
 *
 * <p>Forcing a re-verification writes the durable half only: it forgets the account's device trusts, so the next
 * join is no longer waved through. Freezing a player who is on the server right now is the verification
 * controller's own concern and stays there.
 *
 * <p>Clearing a lockout touches only the in-memory window, which is what a lockout is. When the operator has the
 * lockout written to the ban list as well, that ban is lifted by the unban that lifts every other one.
 */
@NullMarked
public final class SecurityActions implements UxmSecurityActions {

    private final ForceReverification force;
    private final AttemptLimiter limiter;
    private final Scheduler scheduler;

    public SecurityActions(ForceReverification force, AttemptLimiter limiter, Scheduler scheduler) {
        this.force = Objects.requireNonNull(force, "force");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<UxmOutcome> forceVerification(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return AsyncActions.perform(
                scheduler,
                () -> force.force(playerId) == ForceReverification.ForceResult.FORCED
                        ? UxmOutcome.ok()
                        : UxmOutcome.failed(UxmFailure.NOT_FOUND, "that account holds no second factor to force"));
    }

    @Override
    public CompletableFuture<UxmOutcome> clearLockout(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        // In memory, so there is nothing to move off the calling thread; the future is the shape of the surface.
        return CompletableFuture.completedFuture(
                limiter.clearLockout(playerId)
                        ? UxmOutcome.ok()
                        : UxmOutcome.failed(UxmFailure.ALREADY_IN_STATE, "that account is not locked out"));
    }
}
