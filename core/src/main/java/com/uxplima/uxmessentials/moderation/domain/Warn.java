package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One issued warning — a row in the append-only warning history ({@code /warn}). The history is never
 * updated or overwritten, so a target's full record of warnings is preserved and {@code /warns <player>}
 * lists it newest-first.
 *
 * @param issuer who issued the warning
 * @param reason the optional free-text reason
 * @param issuedAt when the warning was issued
 */
public record Warn(Issuer issuer, Optional<String> reason, Instant issuedAt) {

    public Warn {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(issuedAt, "issuedAt");
    }

    /** A warning by {@code issuer} with {@code reason} (nullable) at {@code issuedAt}. */
    public static Warn of(Issuer issuer, @org.jspecify.annotations.Nullable String reason, Instant issuedAt) {
        return new Warn(issuer, Optional.ofNullable(reason).filter(r -> !r.isBlank()), issuedAt);
    }
}
