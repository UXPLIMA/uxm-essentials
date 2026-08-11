package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What the server will make a player prove, and whether it is currently refusing to listen.
 *
 * <p>No factor material is here and none ever will be. A PIN is a one-way hash the store checks internally, and an
 * authenticator secret is the shared secret itself: publishing either would hand out the second factor along with
 * the question of whether one exists.
 *
 * @param playerId the account this is about
 * @param totpEnabled whether they hold an authenticator factor
 * @param pinSet whether they hold a PIN
 * @param enrolledAt when they first enrolled a factor, or empty when they hold none
 * @param lockedOut whether the account is inside a lockout window right now
 */
public record UxmSecurityStatus(
        UUID playerId, boolean totpEnabled, boolean pinSet, Optional<Instant> enrolledAt, boolean lockedOut) {

    public UxmSecurityStatus {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(enrolledAt, "enrolledAt");
    }

    /** Whether the player holds any factor at all, which is what decides whether they are asked to verify. */
    public boolean enrolled() {
        return totpEnabled || pinSet;
    }
}
