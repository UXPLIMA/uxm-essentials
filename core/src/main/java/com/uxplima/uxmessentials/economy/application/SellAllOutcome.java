package com.uxplima.uxmessentials.economy.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.Money;

/**
 * The result of a {@code /sellall}: which materials were sold and in what quantity, plus the total proceeds.
 * A refusal — an empty inventory, nothing priced, or a credit the currency clamp rejected — carries an empty
 * {@link #sold()} map and no proceeds; the use case has already told the seller why through the
 * {@link EconomyNotifier}. The adapter reads {@link #sold()} to decide which stacks to remove from the seller's
 * inventory, so the inventory and the balance never diverge.
 *
 * @param sold the material id → quantity actually sold (empty on a refusal)
 * @param earned the total proceeds when anything sold, otherwise empty
 */
public record SellAllOutcome(Map<String, Integer> sold, Optional<Money> earned) {

    public SellAllOutcome {
        Objects.requireNonNull(sold, "sold");
        Objects.requireNonNull(earned, "earned");
        sold = Map.copyOf(sold);
        if (sold.isEmpty() != earned.isEmpty()) {
            throw new IllegalArgumentException("a completed sale carries both its items and its proceeds");
        }
    }

    /** A completed sale of {@code sold} for {@code amount}. */
    public static SellAllOutcome sold(Map<String, Integer> sold, Money amount) {
        return new SellAllOutcome(sold, Optional.of(Objects.requireNonNull(amount, "amount")));
    }

    /** A refused sale — nothing was credited and nothing should be removed. */
    public static SellAllOutcome refused() {
        return new SellAllOutcome(Map.of(), Optional.empty());
    }
}
