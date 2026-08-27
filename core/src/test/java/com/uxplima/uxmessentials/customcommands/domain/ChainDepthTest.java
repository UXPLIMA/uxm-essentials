package com.uxplima.uxmessentials.customcommands.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ChainDepthTest {

    private final UUID who = UUID.randomUUID();

    @Test
    void allowsUpToTheConfiguredDepthAndRefusesTheNextEntry() {
        ChainDepth depth = new ChainDepth(2);

        assertThat(depth.enter(who)).isTrue();
        assertThat(depth.enter(who)).isTrue();
        assertThat(depth.enter(who)).isFalse();
    }

    @Test
    void aRefusedEntryDoesNotConsumeABudgetSlot() {
        ChainDepth depth = new ChainDepth(1);

        assertThat(depth.enter(who)).isTrue();
        assertThat(depth.enter(who)).isFalse();
        depth.exit(who);

        assertThat(depth.enter(who)).isTrue();
    }

    @Test
    void anExitedPlayerHoldsNoEntryAtAll() {
        ChainDepth depth = new ChainDepth(1);
        depth.enter(who);
        depth.exit(who);

        assertThat(depth.tracked()).isZero();
    }

    @Test
    void oneStuckPlayerNeverBlocksAnother() {
        ChainDepth depth = new ChainDepth(1);
        depth.enter(who);

        assertThat(depth.enter(UUID.randomUUID())).isTrue();
    }

    @Test
    void clearDropsEveryHeldLevel() {
        ChainDepth depth = new ChainDepth(2);
        depth.enter(who);
        depth.enter(who);

        depth.clear();

        assertThat(depth.tracked()).isZero();
        assertThat(depth.enter(who)).isTrue();
    }

    @Test
    void refusesAnImpossibleCeiling() {
        assertThatThrownBy(() -> new ChainDepth(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
