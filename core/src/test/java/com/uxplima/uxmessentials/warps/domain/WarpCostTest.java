package com.uxplima.uxmessentials.warps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * {@link WarpCost} value semantics: the "no charge" case is a first-class {@link WarpCost#free()} value the
 * use case branches on without a null check, a concrete price is a non-negative exact {@link BigDecimal},
 * and a negative price is rejected at construction.
 */
class WarpCostTest {

    @Test
    void freeIsZeroAndReportsItself() {
        assertThat(WarpCost.free().isFree()).isTrue();
        assertThat(WarpCost.free().amount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void aPositivePriceIsNotFree() {
        WarpCost cost = WarpCost.of(new BigDecimal("100.00"));

        assertThat(cost.isFree()).isFalse();
        assertThat(cost.amount()).isEqualByComparingTo("100.00");
    }

    @Test
    void aZeroPriceIsTreatedAsFree() {
        assertThat(WarpCost.of(BigDecimal.ZERO).isFree()).isTrue();
    }

    @Test
    void rejectsANegativePrice() {
        assertThatThrownBy(() -> WarpCost.of(new BigDecimal("-1"))).isInstanceOf(IllegalArgumentException.class);
    }
}
