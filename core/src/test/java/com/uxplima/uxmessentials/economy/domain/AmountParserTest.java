package com.uxplima.uxmessentials.economy.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountParserTest {

    private static final Currency DOLLARS = Currency.builder(CurrencyId.of("dollars"))
            .max(new BigDecimal("1000000000"))
            .build();
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).precision(0).build();

    private static BigDecimal parsed(String raw, Currency currency) {
        return AmountParser.parse(raw, currency).orElseThrow().amount();
    }

    @ParameterizedTest
    @CsvSource({
        "100, 100.00",
        "2.5, 2.50",
        "2.5k, 2500.00",
        "1M, 1000000.00",
        "'1,000', 1000.00",
        "'1.000', 1000.00",
        "'1,000.50', 1000.50",
        "'1.000,50', 1000.50",
        "'12,5', 12.50",
        "'  42 ', 42.00",
        "1234.567, 1234.57"
    })
    void playersCanWriteAmountsTheWayTheyAreUsedTo(String raw, String expected) {
        assertThat(parsed(raw, DOLLARS)).isEqualByComparingTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "1,00,0", "12.34.5", "k", "."})
    void textThatIsNotAnAmountIsRefused(String raw) {
        assertThat(AmountParser.parse(raw, DOLLARS).asError()).contains(AmountParseError.NOT_A_NUMBER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5", "0.001"})
    void zeroNegativeAndRoundedAwayAmountsAreRefused(String raw) {
        assertThat(AmountParser.parse(raw, DOLLARS).asError()).contains(AmountParseError.NOT_POSITIVE);
    }

    @Test
    void aLeadingZeroMakesTheSeparatorADecimalPoint() {
        assertThat(parsed("0.500", DOLLARS)).isEqualByComparingTo("0.50");
        assertThat(parsed("0,750", DOLLARS)).isEqualByComparingTo("0.75");
        assertThat(AmountParser.parse("0.500,25", DOLLARS).asError()).contains(AmountParseError.NOT_A_NUMBER);
    }

    @Test
    void anAmountRoundsToTheCurrencyPrecision() {
        assertThat(parsed("2.3456", DOLLARS)).isEqualByComparingTo("2.35");
        assertThat(parsed("2.6", COINS)).isEqualByComparingTo("3");
    }

    @Test
    void anAmountAboveTheCurrencyMaximumIsCappedAtTheMaximum() {
        assertThat(parsed("5b", DOLLARS)).isEqualByComparingTo("1000000000");
    }
}
