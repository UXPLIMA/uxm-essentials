package com.uxplima.uxmessentials.economy.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Value object representing the conversion rule from a source currency to a target currency.
 */
public final class ExchangeRate {

    private final CurrencyId source;
    private final CurrencyId target;
    private final BigDecimal rate;
    private final BigDecimal feePercent; // e.g. 0.05 for 5% fee

    public ExchangeRate(CurrencyId source, CurrencyId target, BigDecimal rate, BigDecimal feePercent) {
        this.source = Objects.requireNonNull(source, "source");
        this.target = Objects.requireNonNull(target, "target");
        this.rate = Objects.requireNonNull(rate, "rate");
        this.feePercent = Objects.requireNonNull(feePercent, "feePercent");
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive: " + rate);
        }
        if (feePercent.compareTo(BigDecimal.ZERO) < 0 || feePercent.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(
                    "Fee percent must be between 0 (inclusive) and 1 (exclusive): " + feePercent);
        }
    }

    public CurrencyId source() {
        return source;
    }

    public CurrencyId target() {
        return target;
    }

    public BigDecimal rate() {
        return rate;
    }

    public BigDecimal feePercent() {
        return feePercent;
    }

    /**
     * Calculates the amount of target currency received for a given amount of source currency,
     * applying the rate and subtracting the fee.
     */
    public BigDecimal calculateTargetAmount(BigDecimal sourceAmount, int targetPrecision) {
        Objects.requireNonNull(sourceAmount, "sourceAmount");
        BigDecimal gross = sourceAmount.multiply(rate);
        if (feePercent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal fee = gross.multiply(feePercent);
            gross = gross.subtract(fee);
        }
        return gross.setScale(targetPrecision, RoundingMode.HALF_UP);
    }
}
