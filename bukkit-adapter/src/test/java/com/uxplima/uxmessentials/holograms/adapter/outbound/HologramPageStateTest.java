package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class HologramPageStateTest {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Test
    void defaultsToPageZero() {
        HologramPageState state = new HologramPageState();

        assertThat(state.currentPage("spawn", ALICE, 3)).isZero();
    }

    @Test
    void advanceWrapsThroughThePagesAndBackToZero() {
        HologramPageState state = new HologramPageState();

        assertThat(state.advance("spawn", ALICE, 3)).isEqualTo(1);
        assertThat(state.advance("spawn", ALICE, 3)).isEqualTo(2);
        assertThat(state.advance("spawn", ALICE, 3)).isZero();
        assertThat(state.currentPage("spawn", ALICE, 3)).isZero();
    }

    @Test
    void tracksViewersIndependently() {
        HologramPageState state = new HologramPageState();

        state.advance("spawn", ALICE, 3);

        assertThat(state.currentPage("spawn", ALICE, 3)).isEqualTo(1);
        assertThat(state.currentPage("spawn", BOB, 3)).isZero();
    }

    @Test
    void clampsAStoredPageWhenThePageCountShrinks() {
        HologramPageState state = new HologramPageState();
        state.advance("spawn", ALICE, 5); // -> page 1
        state.advance("spawn", ALICE, 5); // -> page 2

        // The hologram now has only two pages: index 2 wraps into [0, 2) -> 0.
        assertThat(state.currentPage("spawn", ALICE, 2)).isZero();
    }

    @Test
    void aSinglePageHologramAlwaysResolvesToZero() {
        HologramPageState state = new HologramPageState();

        assertThat(state.advance("spawn", ALICE, 1)).isZero();
        assertThat(state.currentPage("spawn", ALICE, 1)).isZero();
    }

    @Test
    void clearForgetsOneHologramsPages() {
        HologramPageState state = new HologramPageState();
        state.advance("spawn", ALICE, 3);
        state.advance("shop", ALICE, 3);

        state.clear("spawn");

        assertThat(state.currentPage("spawn", ALICE, 3)).isZero();
        assertThat(state.currentPage("shop", ALICE, 3)).isEqualTo(1);
    }

    @Test
    void clearAllForgetsEveryHologramsPages() {
        HologramPageState state = new HologramPageState();
        state.advance("spawn", ALICE, 3);
        state.advance("shop", BOB, 3);

        state.clearAll();

        assertThat(state.currentPage("spawn", ALICE, 3)).isZero();
        assertThat(state.currentPage("shop", BOB, 3)).isZero();
    }
}
