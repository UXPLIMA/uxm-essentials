package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A punishment a player is serving right now.
 *
 * <p>Only active sanctions are published as one of these: a lapsed ban is history, and history is
 * {@link com.uxplima.uxmessentials.api.query.UxmModerationQuery#history}.
 *
 * @param kind which punishment it is
 * @param playerId who is serving it
 * @param issuer who handed it down
 * @param reason the reason given, or empty when none was
 * @param issuedAt when it was handed down
 * @param expiresAt when it lapses, or empty when it is permanent
 */
public record UxmSanction(
        UxmSanctionKind kind,
        UUID playerId,
        UxmIssuer issuer,
        Optional<String> reason,
        Instant issuedAt,
        Optional<Instant> expiresAt) {

    public UxmSanction {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    /** Whether it stands until somebody lifts it. */
    public boolean isPermanent() {
        return expiresAt.isEmpty();
    }
}
