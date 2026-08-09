package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One warning against a player.
 *
 * <p>Warnings are counted rather than served: enough of them within the operator's window escalates into a real
 * punishment, which is why an expired one is dropped from this list rather than kept with a flag.
 *
 * @param issuer who issued it
 * @param reason the reason given, or empty when none was
 * @param issuedAt when it was issued
 * @param expiresAt when it stops counting, or empty when it counts forever
 */
public record UxmWarn(UxmIssuer issuer, Optional<String> reason, Instant issuedAt, Optional<Instant> expiresAt) {

    public UxmWarn {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
