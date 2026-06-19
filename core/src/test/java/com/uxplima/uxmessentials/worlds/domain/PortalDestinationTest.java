package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PortalDestinationTest {

    @Test
    void accessorsReturnTheComponents() {
        WorldName world = WorldName.of("world_nether");
        PortalDestination destination = new PortalDestination(world, 1.5, 64.0, -2.5);
        assertThat(destination.world()).isEqualTo(world);
        assertThat(destination.x()).isEqualTo(1.5);
        assertThat(destination.y()).isEqualTo(64.0);
        assertThat(destination.z()).isEqualTo(-2.5);
    }

    @SuppressWarnings("NullAway") // deliberately feeds null to verify the constructor rejects it at runtime
    @Test
    void rejectsNullWorld() {
        assertThatNullPointerException().isThrownBy(() -> new PortalDestination(null, 0.0, 0.0, 0.0));
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        WorldName world = WorldName.of("world");
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThatThrownBy(() -> new PortalDestination(world, bad, 0.0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PortalDestination(world, 0.0, bad, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new PortalDestination(world, 0.0, 0.0, bad))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
