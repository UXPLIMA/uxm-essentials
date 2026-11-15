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

    /**
     * The id of the currency fees are taken in, carried with a fee paid for a world on another server so the backend
     * that ends the hop can give back exactly what was taken.
     */
    String currencyId();

    /**
     * Gives {@code amount}, taken in {@code currencyId}, back to {@code who}, returning whether the credit landed. The
     * free implementation credits nothing and always succeeds.
     */
    boolean refund(PlayerRef who, BigDecimal amount, String currencyId);
}
