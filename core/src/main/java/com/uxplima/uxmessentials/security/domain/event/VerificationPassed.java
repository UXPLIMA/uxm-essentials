package com.uxplima.uxmessentials.security.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player proved their second factor and the join freeze was lifted.
 *
 * <p>Only for a proof that was actually made. A player who holds no factor is never asked for one and passes
 * nothing, and a returning player waved through by a remembered device did not prove anything either.
 *
 * @param player the player who proved it
 */
public record VerificationPassed(PlayerRef player) implements SecurityEvent {

    public VerificationPassed {
        Objects.requireNonNull(player, "player");
    }
}
