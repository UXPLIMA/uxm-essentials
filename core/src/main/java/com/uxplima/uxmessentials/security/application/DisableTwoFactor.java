package com.uxplima.uxmessentials.security.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRegistration;
import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;

/**
 * Remove a player's two-factor registration ({@code /2fa disable <code|pin>}), but only after they prove a current
 * factor — a valid TOTP code or their PIN. Requiring the proof is the point: it stops someone who has walked up to
 * an unlocked session from simply turning the protection off. The single submitted value is checked against whatever
 * factors the player holds, so either a code or a PIN unlocks the removal.
 */
public final class DisableTwoFactor {

    private final TwoFactorRepository repository;
    private final int codeWindow;

    public DisableTwoFactor(TwoFactorRepository repository, int codeWindow) {
        this.repository = Objects.requireNonNull(repository, "repository");
        if (codeWindow < 0) {
            throw new IllegalArgumentException("codeWindow must not be negative: " + codeWindow);
        }
        this.codeWindow = codeWindow;
    }

    /** Disable the player's factors if {@code factor} (a code or PIN) proves a current one at {@code now}. */
    public DisableResult disable(UUID playerId, String factor, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(factor, "factor");
        Objects.requireNonNull(now, "now");
        TwoFactorRegistration registration = repository.find(playerId).orElse(null);
        if (registration == null || !registration.hasAnyFactor()) {
            return DisableResult.NOT_ENROLLED;
        }
        if (!proves(registration, playerId, factor, now)) {
            return DisableResult.INVALID_FACTOR;
        }
        repository.delete(playerId);
        return DisableResult.DISABLED;
    }

    /** True when {@code factor} verifies against the player's TOTP secret or their stored PIN. */
    private boolean proves(TwoFactorRegistration registration, UUID playerId, String factor, Instant now) {
        TwoFactorSecret secret = registration.secret();
        if (secret != null && TotpCode.verify(secret, factor, now, codeWindow)) {
            return true;
        }
        return registration.pinSet() && repository.verifyPin(playerId, factor);
    }
}
