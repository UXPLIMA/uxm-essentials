package com.uxplima.uxmessentials.shared.application.hop;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What the origin debited for a hop, in the plain terms every context's economy seam speaks: an exact amount and
 * the currency id it was taken in. It travels with the hop so that whichever backend ends the hop can give back
 * exactly this, or settle exactly this.
 *
 * @param amount the debited amount, never negative
 * @param currencyId the currency the amount was taken in
 */
public record HopCharge(BigDecimal amount, String currencyId) {

    public HopCharge {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("a hop charge must not be negative: " + amount);
        }
    }
}
