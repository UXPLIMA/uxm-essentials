package com.uxplima.uxmessentials.economy.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.fakes.Currencies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Coverage of {@link MoneyFormat} rendering. The full form keeps every digit grouped by the currency pattern
 * and is the default; the compact form abbreviates large figures with a magnitude suffix while leaving the
 * stored value untouched. Both stay locale-stable (ROOT grouping) so a balance reads identically for every
 * viewer.
 */
class MoneyFormatTest {

    @Test
    void fullIsTheDefaultAndGroupsDigits() {
        Money amount = Money.of(Currencies.COINS, new BigDecimal("1234567.50"));

        assertThat(MoneyFormat.amount(amount)).isEqualTo("1,234,567.50");
        assertThat(MoneyFormat.withSymbol(amount)).isEqualTo("$1,234,567.50");
        assertThat(MoneyFormat.amount(amount, AmountFormat.FULL)).isEqualTo("1,234,567.50");
    }

    @ParameterizedTest
    @CsvSource({
        "999, 999",
        "1000, 1K",
        "1500, 1.5K",
        "1234567.50, 1.23M",
        "2000000000, 2B",
        "1000000000000, 1T",
        "1234, 1.23K",
    })
    void compactAbbreviatesLargeFigures(String stored, String rendered) {
        Money amount = Money.of(Currencies.COINS, new BigDecimal(stored));

        assertThat(MoneyFormat.amount(amount, AmountFormat.COMPACT)).isEqualTo(rendered);
    }

    @Test
    void compactKeepsTheCurrencySymbol() {
        Money amount = Money.of(Currencies.COINS, new BigDecimal("1500000"));

        assertThat(MoneyFormat.withSymbol(amount, AmountFormat.COMPACT)).isEqualTo("$1.5M");
    }

    @Test
    void compactRendersSubThousandWithoutASuffix() {
        Money amount = Money.of(Currencies.COINS, new BigDecimal("42.50"));

        assertThat(MoneyFormat.amount(amount, AmountFormat.COMPACT)).isEqualTo("42.5");
    }

    @Test
    void configTokenRoundTripsThroughAmountFormat() {
        assertThat(AmountFormat.fromConfig("compact")).isEqualTo(AmountFormat.COMPACT);
        assertThat(AmountFormat.fromConfig("FULL")).isEqualTo(AmountFormat.FULL);
        assertThat(AmountFormat.fromConfig("nonsense")).isEqualTo(AmountFormat.FULL);
        assertThat(AmountFormat.fromConfig("")).isEqualTo(AmountFormat.FULL);
        assertThat(AmountFormat.COMPACT.token()).isEqualTo("compact");
    }
}
