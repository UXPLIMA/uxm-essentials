package com.uxplima.uxmessentials.customcommands.domain;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChainDepthTest {

    @Test
    void aReplacingRunMayGoOneLevelPastTheCeilingAndNoFurther() {
        ChainDepth depth = new ChainDepth(1);

        assertThat(depth.enter(ALEX.uuid())).isTrue();
        assertThat(depth.enter(ALEX.uuid()))
                .as("an ordinary run stops at the ceiling")
                .isFalse();
        assertThat(depth.enterReplacing(ALEX.uuid())).isTrue();
        assertThat(depth.enterReplacing(ALEX.uuid()))
                .as("a replacing run gets one level of headroom, not more")
                .isFalse();
    }

    @Test
    void aRefusedReplacingClaimConsumesNothing() {
        ChainDepth depth = new ChainDepth(1);
        depth.enter(ALEX.uuid());
        depth.enterReplacing(ALEX.uuid());

        depth.enterReplacing(ALEX.uuid());
        depth.exit(ALEX.uuid());
        depth.exit(ALEX.uuid());

        assertThat(depth.tracked()).isZero();
    }
}
