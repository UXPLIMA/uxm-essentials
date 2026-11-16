package com.uxplima.uxmessentials.customcommands.domain;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChainDepthTest {

    @Test
    void aRunStopsAtTheCeilingAndARefusedClaimConsumesNothing() {
        ChainDepth depth = new ChainDepth(1);

        assertThat(depth.enter(ALEX.uuid())).isTrue();
        assertThat(depth.enter(ALEX.uuid())).as("the ceiling is reached").isFalse();
        depth.exit(ALEX.uuid());

        assertThat(depth.tracked()).isZero();
    }
}
