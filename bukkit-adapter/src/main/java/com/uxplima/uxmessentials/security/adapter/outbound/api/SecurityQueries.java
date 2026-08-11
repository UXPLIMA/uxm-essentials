package com.uxplima.uxmessentials.security.adapter.outbound.api;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.query.UxmSecurityQuery;
import com.uxplima.uxmessentials.api.view.UxmSecurityStatus;
import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.AsyncQueries;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The published enrolment read.
 *
 * <p>It reads the registration the verifier reads and reports only its shape: which factors exist and when the
 * account first enrolled. The decrypted authenticator secret comes back on that row because verification needs it,
 * and it stops here.
 *
 * <p>The registration is a database row, so it goes to a worker. The lockout is an in-memory window, so it is
 * answered where the caller stands.
 */
@NullMarked
public final class SecurityQueries implements UxmSecurityQuery {

    private final TwoFactorRepository repository;
    private final AttemptLimiter limiter;
    private final Scheduler scheduler;
    private final Clock clock;

    public SecurityQueries(TwoFactorRepository repository, AttemptLimiter limiter, Scheduler scheduler, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletableFuture<UxmSecurityStatus> of(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        boolean lockedOut = isLockedOut(playerId);
        return AsyncQueries.supply(
                scheduler, () -> view(playerId, repository.find(playerId).orElse(null), lockedOut));
    }

    @Override
    public boolean isLockedOut(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return limiter.isLockedOut(playerId, clock.instant());
    }

    /** An account with no row holds no factor, which is a real answer rather than an absent one. */
    private static UxmSecurityStatus view(
            UUID playerId, @org.jspecify.annotations.Nullable TwoFactorRegistration registration, boolean lockedOut) {
        if (registration == null) {
            return new UxmSecurityStatus(playerId, false, false, Optional.empty(), lockedOut);
        }
        return new UxmSecurityStatus(
                playerId,
                registration.totpEnabled(),
                registration.pinSet(),
                Optional.of(registration.enrolledAt()),
                lockedOut);
    }
}
