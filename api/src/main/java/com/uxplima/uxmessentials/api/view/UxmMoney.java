package com.uxplima.uxmessentials.api.view;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An amount of one currency.
 *
 * <p>The currency is its configured id ({@code "default"} unless the operator declared more), not a symbol or a
 * display name, so two figures can be compared without going through formatting. The amount is already scaled to
 * that currency's precision, which is why it is a {@link BigDecimal} and not a double: money that rounds differently
 * in different places is money that goes missing.
 *
 * @param currency the currency's configured id
 * @param amount the figure, scaled to the currency's precision
 */
public record UxmMoney(String currency, BigDecimal amount) {

    public UxmMoney {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(amount, "amount");
    }
}
