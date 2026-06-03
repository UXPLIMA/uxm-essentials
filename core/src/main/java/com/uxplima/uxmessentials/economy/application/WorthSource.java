package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * The read seam every worth consumer ({@code /worth}, {@code /sell}, {@code /sellall}) depends on instead of a
 * concrete table: the per-item sell value as a case-insensitive material-id → unit-price lookup. A price is per
 * single item in the economy's default currency, so a stack value is the unit price times the amount; a
 * material with no worth yields an empty {@link Optional} rather than a zero, so the caller can tell
 * "not sellable" from "free".
 *
 * <p>The operator-configured {@link WorthTable} is the static implementation, and {@link CombiningWorthSource}
 * layers the in-game {@code /setworth} overrides over it (store first, config fallback) so a freshly set price
 * is visible to a sell on the next call without a rewire.
 */
public interface WorthSource {

    /** The unit price for {@code material}, or empty when the material has no worth. */
    Optional<BigDecimal> unitPrice(String material);

    /** The value of {@code amount} of {@code material}, or empty when the material has no worth. */
    default Optional<BigDecimal> stackValue(String material, int amount) {
        Objects.requireNonNull(material, "material");
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        return unitPrice(material).map(price -> price.multiply(BigDecimal.valueOf(amount)));
    }
}
