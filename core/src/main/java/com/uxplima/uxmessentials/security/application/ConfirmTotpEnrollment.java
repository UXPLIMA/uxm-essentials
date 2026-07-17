package com.uxplima.uxmessentials.security.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.port.TwoFactorRepository;
import com.uxplima.uxmessentials.security.domain.TotpCode;
import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;

/**
 * The second step of TOTP enrolment ({@code /2fa confirm <code>}): verify a code against the pending secret from
 * {@link BeginTotpEnrollment}, and only on success persist that secret (encrypted, by the repository) as the
 * player's enabled factor. Requiring a confirming code before the factor takes effect is the safety property that
 * keeps a mistyped or never-scanned secret from silently becoming a lockout at the Phase-2 join check.
 */
public final class ConfirmTotpEnrollment {

    private final TwoFactorRepository repository;
    private final PendingTotpEnrollments pending;
    private final int codeWindow;

    public ConfirmTotpEnrollment(TwoFactorRepository repository, PendingTotpEnrollments pending, int codeWindow) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.pending = Objects.requireNonNull(pending, "pending");
        if (codeWindow < 0) {
            throw new IllegalArgumentException("codeWindow must not be negative: " + codeWindow);
        }
        this.codeWindow = codeWindow;
    }

    /** Confirm {@code code} at {@code now} against the player's pending secret, enabling the factor on success. */
    public TotpConfirmResult confirm(UUID playerId, String code, Instant now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(now, "now");
        TwoFactorSecret secret = pending.pending(playerId).orElse(null);
        if (secret == null) {
            return TotpConfirmResult.NO_PENDING;
        }
        if (!TotpCode.verify(secret, code, now, codeWindow)) {
            return TotpConfirmResult.INVALID_CODE;
        }
        repository.enableTotp(playerId, secret);
        pending.clear(playerId);
        return TotpConfirmResult.ENABLED;
    }
}
