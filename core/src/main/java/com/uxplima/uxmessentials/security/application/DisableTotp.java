package com.uxplima.uxmessentials.security.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;

/**
 * Remove a player's authenticator factor ({@code /2fa disable <code>}), but only after they prove it with a current
 * code from that same authenticator. Requiring the proof is the point: it stops someone who has walked up to an
 * unlocked session from simply turning the protection off.
 *
 * <p>The proof is <b>TOTP only</b>. A PIN neither unlocks this nor is removed by it: the two factors are separate
 * protections a player enrols separately, so the weaker one must never be able to strip the stronger. A player who
 * has lost their authenticator goes through an operator ({@link ResetFactors}), which leaves a log line behind,
 * rather than falling back to their PIN. Removing a PIN is {@link RemovePin}'s job.
 *
 * <p>Because a correct guess here removes the protection, this path shares the {@link AttemptLimiter} with the join
 * freeze and op re-auth: a locked-out account is refused outright, and every wrong code counts against the same
 * durable, account-scoped budget, so {@code /2fa disable} cannot be spammed to brute-force a code.
 */
public final class DisableTotp {

    private final TwoFactorRepository repository;
    private final AttemptLimiter limiter;
    private final int codeWindow;

    public DisableTotp(TwoFactorRepository repository, AttemptLimiter limiter, int codeWindow) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.limiter = Objects.requireNonNull(limiter, "limiter");
        if (codeWindow < 0) {
            throw new IllegalArgumentException("codeWindow must not be negative: " + codeWindow);
        }
        this.codeWindow = codeWindow;
    }

    /** Remove the player's TOTP factor if {@code code} proves it at {@code now}, leaving any PIN untouched. */
    public DisableResult disable(UUID playerId, String code, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(now, "now");
        if (limiter.isLockedOut(playerId, now)) {
            return DisableResult.LOCKED_OUT;
        }
        TwoFactorRegistration registration = repository.find(playerId).orElse(null);
        TwoFactorSecret secret = registration == null ? null : registration.secret();
        if (secret == null) {
            return DisableResult.NOT_ENROLLED;
        }
        if (!TotpCode.verify(secret, code, now, codeWindow)) {
            return limiter.recordFailure(playerId, now).lockedOut()
                    ? DisableResult.LOCKED_OUT
                    : DisableResult.INVALID_CODE;
        }
        limiter.recordSuccess(playerId);
        repository.clearTotp(playerId);
        return DisableResult.DISABLED;
    }
}
