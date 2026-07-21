package com.uxplima.uxmessentials.trade.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable snapshot of what one side has staked at a moment in time: the {@link OfferedItem} stacks, a money amount
 * per currency, and a whole-number experience-point amount. It is a whole-value replacement, the adapter renders the
 * live inventory view into a fresh {@code TradeOffer} and hands it to {@link TradeSession#withOffer}, so the session
 * never mutates an offer in place.
 *
 * <p>Money is keyed by currency id (the string an operator lists under {@code currencies-allowed}); the adapter maps
 * each id to a real economy currency when it charges the offer on commit. Amounts are non-negative, a zero or absent
 * entry means "no money of that currency staked". Experience is a single non-negative count of experience points (not
 * levels), so a partial amount is exact; zero means no experience is staked. The domain treats experience as a plain
 * number, the adapter reads and grants the real player experience through the Bukkit API at the boundary.
 *
 * @param items the staked stacks, in the order the player placed them; defensively copied and never {@code null}
 * @param money the amount staked per currency id; defensively copied, no {@code null}/blank keys, no negative amounts
 * @param experience the whole experience points staked; never negative, zero when no experience is staked
 */
public record TradeOffer(List<OfferedItem> items, Map<String, BigDecimal> money, long experience) {

    private static final TradeOffer EMPTY = new TradeOffer(List.of(), Map.of(), 0L);

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
        if (experience < 0) {
            throw new IllegalArgumentException("experience must not be negative: " + experience);
        }
    }

    /** An offer of items and money with no staked experience, so existing item/money call sites stay unchanged. */
    public TradeOffer(List<OfferedItem> items, Map<String, BigDecimal> money) {
        this(items, money, 0L);
    }

    /** The empty offer a side starts a session with, no items, no money, no experience. */
    public static TradeOffer empty() {
        return EMPTY;
    }

    /** True when nothing is staked, no items, no non-zero money entry, and no experience. */
    public boolean isEmpty() {
        return items.isEmpty() && money.isEmpty() && experience == 0L;
    }
}
