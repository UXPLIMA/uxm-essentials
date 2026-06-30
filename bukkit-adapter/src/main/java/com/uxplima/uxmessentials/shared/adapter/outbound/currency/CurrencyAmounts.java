package com.uxplima.uxmessentials.shared.adapter.outbound.currency;

/** Number rendering shared by the providers that have no back-end formatter of their own (Exp, the point-based reflection back-ends). */
final class CurrencyAmounts {

    private CurrencyAmounts() {}

    /** A whole number without a trailing {@code .0}; otherwise the plain double rendering. */
    static String plain(double amount) {
        if (Double.isFinite(amount) && amount == Math.rint(amount)) {
            return Long.toString((long) amount);
        }
        return Double.toString(amount);
    }
}
