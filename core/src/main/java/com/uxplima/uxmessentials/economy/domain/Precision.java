package com.uxplima.uxmessentials.economy.domain;

/**
 * How finely a backend can actually hold an amount. A currency's configured {@code precision} decides how a
 * {@link Money} is scaled; this says whether the backend behind it can honour that scale at all. PlayerPoints
 * and Paper experience count whole units, so an amount destined for them is rounded once, here, rather than
 * silently truncated deep inside a foreign plugin.
 */
public enum Precision {
    /** Whole units only. An amount is rounded HALF_UP before it reaches the backend. */
    INTEGRAL,
    /** Fractional units, to the currency's configured precision. */
    DECIMAL
}
