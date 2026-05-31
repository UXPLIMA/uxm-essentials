package com.uxplima.uxmessentials.itemworld.domain;

import java.util.Optional;

/**
 * A validated item amount for {@code /give}, {@code /item} and {@code /more}, clamped to a configured cap.
 *
 * <p>The cap is the {@code /give}//{@code /more} cap an operator sets in {@code itemworld.conf}
 * (docs/09-deployment.md §itemworld.conf). The rule is intentionally a domain concern so the cap is enforced
 * once, before the adapter ever materialises a stack: a non-positive amount is invalid; an amount above the
 * cap is rejected (never silently truncated), so the actor learns the limit rather than receiving a smaller
 * stack than asked for.
 *
 * @param amount the validated, in-range amount (always {@code 1..cap})
 */
public record AmountSpec(int amount) {

    /** The largest amount a stack-fill or give may request when an operator leaves the cap unset. */
    public static final int DEFAULT_CAP = 2304; // 36 slots × 64

    public AmountSpec {
        if (amount < 1) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
    }

    /**
     * Validate {@code requested} against {@code cap}: present when {@code 1 <= requested <= cap}, empty for a
     * non-positive or over-cap amount. A use case maps the empty case to {@link ItemWorldError#AMOUNT_OUT_OF_RANGE}.
     */
    public static Optional<AmountSpec> of(int requested, int cap) {
        int effectiveCap = cap < 1 ? DEFAULT_CAP : cap;
        if (requested < 1 || requested > effectiveCap) {
            return Optional.empty();
        }
        return Optional.of(new AmountSpec(requested));
    }
}
