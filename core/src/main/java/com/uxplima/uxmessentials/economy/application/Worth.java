package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Semantics of an item's sell worth: an exact decimal amount and its currency ID.
 */
public record Worth(BigDecimal amount, String currencyId) {

    public Worth {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("worth amount must not be negative: " + amount);
        }
    }

    /** A worth in the given currency. */
    public static Worth of(BigDecimal amount, String currencyId) {
        return new Worth(amount, currencyId);
    }
}
