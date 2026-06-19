package com.uxplima.uxmessentials.worlds.application.port;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Charges the configured per-world entry fee against a player's economy balance. A no-op (free)
 * implementation is supplied when the economy module is disabled, so the worlds context can depend on
 * this port unconditionally and never branch on whether economy is present.
 */
public interface WorldEntryFee {

    /** Whether {@code who} can cover {@code amount}; always {@code true} for the free implementation. */
    boolean canAfford(PlayerRef who, BigDecimal amount);

    /**
     * Deducts {@code amount} from {@code who}, returning whether the charge succeeded. The free
     * implementation deducts nothing and always succeeds.
     */
    boolean charge(PlayerRef who, BigDecimal amount);
}
