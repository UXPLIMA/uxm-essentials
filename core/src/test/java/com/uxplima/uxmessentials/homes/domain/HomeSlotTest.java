package com.uxplima.uxmessentials.homes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** {@link HomeSlot} construction, index, display number, and rejection of negative indices. */
class HomeSlotTest {

    @Test
    void slotZeroIsValid() {
        HomeSlot slot = HomeSlot.of(0);
        assertThat(slot.index()).isEqualTo(0);
    }

    @Test
    void displayNumberIsOneBased() {
        assertThat(HomeSlot.of(0).displayNumber()).isEqualTo(1);
        assertThat(HomeSlot.of(4).displayNumber()).isEqualTo(5);
    }

    @Test
    void positiveIndexIsAccepted() {
        assertThat(HomeSlot.of(9).index()).isEqualTo(9);
    }

    @Test
    void negativeIndexIsRejected() {
        assertThatThrownBy(() -> HomeSlot.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void factoryAndDirectConstructionAreEquivalent() {
        assertThat(HomeSlot.of(3)).isEqualTo(new HomeSlot(3));
    }
}
