package com.uxplima.uxmessentials.economy.application.port;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A balance that can only change while its player is online on this server: the currency items in their inventory,
 * or a backend such as experience that has no offline account. Nothing is dropped on the ground: what does not fit is
 * reported, so the caller keeps it for later. A change whose outcome is not known (the player's thread did not
 * answer) is reported as not done, never as a zero balance.
 */
public interface ItemPurse {

    /** Put up to {@code amount} into {@code owner}'s inventory; how much fit, or empty when they are not online here. */
    Optional<BigDecimal> give(PlayerRef owner, Money amount);

    /**
     * Bring {@code owner}'s held amount to {@code target}, taking items (with change) or adding what fits; the amount
     * before and after, or empty when they are not online here.
     */
    Optional<Change> setTo(PlayerRef owner, Money target);

    /** A held amount before and after a change. */
    record Change(BigDecimal before, BigDecimal after) {

        public Change {
            Objects.requireNonNull(before, "before");
            Objects.requireNonNull(after, "after");
        }
    }
}
