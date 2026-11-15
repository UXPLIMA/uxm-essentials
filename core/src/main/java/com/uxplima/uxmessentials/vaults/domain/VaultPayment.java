package com.uxplima.uxmessentials.vaults.domain;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * What the owner actually paid to create a vault, in the currency it was taken in. It is the ceiling on the delete
 * refund: a refund larger than the payment would turn create and delete into a loop that prints money, so the
 * configured refund is only ever paid up to this amount. A vault that cost nothing carries no payment at all
 * rather than a zero one, so "free" has exactly one representation.
 *
 * <p>The currency is the economy's own id kept as plain text, so the vaults context names a currency without
 * importing an economy type. The refund goes back in this currency even if the server's default has changed
 * since, because paying it in a different currency could be worth more than what was taken.
 *
 * @param amount the create fee that was withdrawn, strictly positive
 * @param currencyId the id of the currency the fee was withdrawn in
 */
public record VaultPayment(BigDecimal amount, String currencyId) {

    public VaultPayment {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currencyId, "currencyId");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("a vault payment must be positive: " + amount);
        }
        if (currencyId.isBlank()) {
            throw new IllegalArgumentException("a vault payment needs a currency");
        }
    }

    /** The part of {@code configured} that may be refunded: the configured refund, never more than was paid. */
    public BigDecimal refundable(BigDecimal configured) {
        Objects.requireNonNull(configured, "configured");
        if (configured.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return configured.min(amount);
    }
}
