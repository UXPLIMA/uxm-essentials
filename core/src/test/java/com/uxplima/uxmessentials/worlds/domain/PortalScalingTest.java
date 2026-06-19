package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class PortalScalingTest {

    @Test
    void normalToNetherCompressesByEight() {
        assertThat(PortalScaling.scale(WorldEnvironment.NORMAL, WorldEnvironment.NETHER))
                .isEqualTo(0.125);
    }

    @Test
    void netherToNormalExpandsByEight() {
        assertThat(PortalScaling.scale(WorldEnvironment.NETHER, WorldEnvironment.NORMAL))
                .isEqualTo(8.0);
    }

    @Test
    void sameEnvironmentDoesNotScale() {
        assertThat(PortalScaling.scale(WorldEnvironment.NORMAL, WorldEnvironment.NORMAL))
                .isEqualTo(1.0);
    }

    @Test
    void normalToTheEndDoesNotScale() {
        assertThat(PortalScaling.scale(WorldEnvironment.NORMAL, WorldEnvironment.THE_END))
                .isEqualTo(1.0);
    }

    @Test
    void netherToTheEndExpandsByEight() {
        assertThat(PortalScaling.scale(WorldEnvironment.NETHER, WorldEnvironment.THE_END))
                .isEqualTo(8.0);
    }

    @SuppressWarnings("NullAway") // deliberately feeds null to verify the method rejects it at runtime
    @Test
    void rejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> PortalScaling.scale(null, WorldEnvironment.NORMAL));
        assertThatNullPointerException().isThrownBy(() -> PortalScaling.scale(WorldEnvironment.NORMAL, null));
    }
}
