package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;

/**
 * Renders a {@link Money} amount into the placeholder string a {@link EconomyMessageKey} interpolates — the
 * numeric value formatted to the currency's {@code format} pattern and tagged with its symbol. This is the
 * <em>value</em> a {@code MessageKey} carries, never a standalone user-facing message, so it stays clear of
 * the inline-literal and {@code String.format}-for-chat rules: the surrounding sentence is the catalog
 * string; this is the amount slotted into its {@code {amount}} placeholder.
 *
 * <p>Formatting is locale-stable on purpose — economy amounts use {@link Locale#ROOT} grouping so a
 * balance reads identically regardless of the viewer's locale, while the catalog sentence around it is
 * localized as usual. The currency's configured {@code format} pattern drives the digit grouping and the
 * decimal places.
 */
public final class MoneyFormat {

    private MoneyFormat() {}

    /** The amount of {@code money} formatted by its currency pattern, e.g. {@code 1,234.50}. */
    public static String amount(Money money) {
        Objects.requireNonNull(money, "money");
        Currency currency = money.currency();
        DecimalFormat format = new DecimalFormat(currency.format(), DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setParseBigDecimal(true);
        return format.format(scaled(money));
    }

    /** The amount with the currency symbol applied, e.g. {@code $1,234.50}. */
    public static String withSymbol(Money money) {
        return Objects.requireNonNull(money, "money").currency().symbol() + amount(money);
    }

    private static BigDecimal scaled(Money money) {
        return money.amount();
    }
}
