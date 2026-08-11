package com.uxplima.uxmessentials.api.view;

import java.util.Objects;

/**
 * One rung of the rank ladder, as the operator configured it.
 *
 * <p>The cost is what a {@code /rankup} onto this rank charges, zero when it is free. The order is the rung's
 * position on the ladder, ascending, so two ranks can be compared without knowing the ladder itself.
 *
 * @param id the rank's configured id, which is what commands and permissions use
 * @param displayName the name shown to players, which may carry colour
 * @param order the rung's position on the ladder, lowest first
 * @param cost what advancing onto this rank charges, zero when free
 */
public record UxmRank(String id, String displayName, int order, long cost) {

    public UxmRank {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        if (cost < 0L) {
            throw new IllegalArgumentException("a rank cost is never negative: " + cost);
        }
    }
}
