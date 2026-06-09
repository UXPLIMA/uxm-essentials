package com.uxplima.uxmessentials.economy.domain;

import java.math.BigDecimal;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Immutable value object representing a physical coin denomination of a currency.
 */
public record Denomination(BigDecimal value, String material, String displayName, @Nullable Integer customModelData) {
    public Denomination {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(displayName, "displayName");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Denomination value must be greater than zero: " + value);
        }
    }
}
