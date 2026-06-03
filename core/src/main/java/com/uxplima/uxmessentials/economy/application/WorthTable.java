package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The operator-configured per-item sell value: a case-insensitive material-id → unit-price map, loaded once
 * from the economy config (no DB, no migration — a worth is a pricing policy, not persisted player state).
 * The price is per single item in the economy's default currency, so a stack value is the unit price times
 * the amount. A material absent from the table has no worth and is "not sellable" — the lookup yields an empty
 * {@link Optional} rather than defaulting to zero, so {@code /worth} and {@code /sell} can tell the two apart.
 */
public final class WorthTable {

    private final Map<String, BigDecimal> prices;

    public WorthTable(Map<String, BigDecimal> prices) {
        Objects.requireNonNull(prices, "prices");
        Map<String, BigDecimal> normalized = new HashMap<>();
        prices.forEach((material, price) -> normalized.put(
                Objects.requireNonNull(material, "material").toLowerCase(Locale.ROOT),
                Objects.requireNonNull(price, "price")));
        this.prices = Map.copyOf(normalized);
    }

    /** An empty table — every material is unpriced. */
    public static WorthTable empty() {
        return new WorthTable(Map.of());
    }

    /** The configured unit price for {@code material}, or empty when the material has no worth. */
    public Optional<BigDecimal> unitPrice(String material) {
        Objects.requireNonNull(material, "material");
        return Optional.ofNullable(prices.get(material.toLowerCase(Locale.ROOT)));
    }

    /** The value of {@code amount} of {@code material}, or empty when the material has no worth. */
    public Optional<BigDecimal> stackValue(String material, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative: " + amount);
        }
        return unitPrice(material).map(price -> price.multiply(BigDecimal.valueOf(amount)));
    }
}
