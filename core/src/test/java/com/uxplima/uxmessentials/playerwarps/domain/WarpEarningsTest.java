package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.BigRange;
import org.junit.jupiter.api.Test;

class WarpEarningsTest {

    private static final String CURRENCY = "coins";

    @Test
    void zeroStartsEmptyInTheGivenCurrency() {
        WarpEarnings earnings = WarpEarnings.zero(CURRENCY);
        assertThat(earnings.amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(earnings.currencyId()).isEqualTo(CURRENCY);
        assertThat(earnings.isZero()).isTrue();
    }

    @Test
    void ofKeepsTheExactAmount() {
        WarpEarnings earnings = WarpEarnings.of(new BigDecimal("12.34"), CURRENCY);
        assertThat(earnings.amount()).isEqualByComparingTo("12.34");
        assertThat(earnings.isZero()).isFalse();
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> WarpEarnings.of(new BigDecimal("-0.01"), CURRENCY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // verifies the compact constructor rejects a literal null in each field
    void rejectsNullFields() {
        assertThatNullPointerException().isThrownBy(() -> new WarpEarnings(null, CURRENCY));
        assertThatNullPointerException().isThrownBy(() -> new WarpEarnings(BigDecimal.ONE, null));
    }

    @Test
    void plusAccruesMoreKeepingTheCurrency() {
        WarpEarnings after = WarpEarnings.of(new BigDecimal("5.00"), CURRENCY).plus(new BigDecimal("2.50"));
        assertThat(after.amount()).isEqualByComparingTo("7.50");
        assertThat(after.currencyId()).isEqualTo(CURRENCY);
    }

    @Test
    void plusSettlesPartOfTheBalanceWithANegativeDelta() {
        WarpEarnings after = WarpEarnings.of(new BigDecimal("5.00"), CURRENCY).plus(new BigDecimal("-3.00"));
        assertThat(after.amount()).isEqualByComparingTo("2.00");
    }

    @Test
    void plusRejectsADeltaThatWouldDriveTheBalanceNegative() {
        WarpEarnings earnings = WarpEarnings.of(new BigDecimal("1.00"), CURRENCY);
        assertThatThrownBy(() -> earnings.plus(new BigDecimal("-1.01"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // verifies plus rejects a literal null delta
    void plusRejectsNullDelta() {
        WarpEarnings earnings = WarpEarnings.zero(CURRENCY);
        assertThatNullPointerException().isThrownBy(() -> earnings.plus(null));
    }

    @Property
    void plusNeverYieldsANegativeBalance(
            @ForAll @BigRange(min = "0", max = "1000000") BigDecimal start,
            @ForAll @BigRange(min = "-1000000", max = "1000000") BigDecimal delta) {
        WarpEarnings earnings = WarpEarnings.of(start, CURRENCY);
        if (start.add(delta).signum() < 0) {
            assertThatThrownBy(() -> earnings.plus(delta)).isInstanceOf(IllegalArgumentException.class);
        } else {
            assertThat(earnings.plus(delta).amount()).isEqualByComparingTo(start.add(delta));
        }
    }
}
