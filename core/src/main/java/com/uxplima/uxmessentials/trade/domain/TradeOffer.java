package com.uxplima.uxmessentials.trade.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable snapshot of what one side has staked at a moment in time: the {@link OfferedItem} stacks and a money
 * amount per currency. It is a whole-value replacement — the adapter renders the live inventory view into a fresh
 * {@code TradeOffer} and hands it to {@link TradeSession#withOffer}, so the session never mutates an offer in place.
 *
 * <p>Money is keyed by currency id (the string an operator lists under {@code currencies-allowed}); the adapter maps
 * each id to a real economy currency when it charges the offer on commit. Amounts are non-negative — a zero or absent
 * entry means "no money of that currency staked".
 *
 * @param items the staked stacks, in the order the player placed them; defensively copied and never {@code null}
 * @param money the amount staked per currency id; defensively copied, no {@code null}/blank keys, no negative amounts
 */
public record TradeOffer(List<OfferedItem> items, Map<String, BigDecimal> money) {

    private static final TradeOffer EMPTY = new TradeOffer(List.of(), Map.of());

    public TradeOffer {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(money, "money");
        items = List.copyOf(items);
        money = Map.copyOf(money);
        for (Map.Entry<String, BigDecimal> entry : money.entrySet()) {
            if (entry.getKey().isBlank()) {
                throw new IllegalArgumentException("money currency id must not be blank");
            }
            if (entry.getValue().signum() < 0) {
                throw new IllegalArgumentException(
                        "money amount must not be negative for currency " + entry.getKey() + ": " + entry.getValue());
            }
        }
    }

    /** The empty offer a side starts a session with — no items, no money. */
    public static TradeOffer empty() {
        return EMPTY;
    }

    /** True when nothing is staked — no items and no non-zero money entry. */
    public boolean isEmpty() {
        return items.isEmpty() && money.isEmpty();
    }
}
