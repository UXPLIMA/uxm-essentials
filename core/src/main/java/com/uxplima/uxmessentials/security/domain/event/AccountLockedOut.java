package com.uxplima.uxmessentials.security.domain.event;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * An account ran out of attempts and no surface will accept another proof from it for a while.
 *
 * <p>{@code banned} says whether the lockout was written to the server's own ban list, which is what the operator
 * gets when the ban surface is there and they asked for it. When it is false the lockout is the in-memory window
 * alone and a restart forgets it.
 *
 * @param player the account that was locked out
 * @param lockout how long it lasts
 * @param banned whether it was also recorded as an ordinary tempban
 */
public record AccountLockedOut(PlayerRef player, Duration lockout, boolean banned) implements SecurityEvent {

    public AccountLockedOut {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(lockout, "lockout");
        if (lockout.isNegative()) {
            throw new IllegalArgumentException("lockout must not be negative: " + lockout);
        }
    }
}
