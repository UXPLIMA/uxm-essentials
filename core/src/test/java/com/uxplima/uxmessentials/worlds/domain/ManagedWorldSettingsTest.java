package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ManagedWorldSettingsTest {

    @Test
    void createdWorldHasDefaultSettings() {
        ManagedWorld w =
                ManagedWorld.created(WorldName.of("w"), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH);
        assertThat(w.settings().raw()).isEmpty();
    }

    @Test
    void withSettingsReplacesOnlySettings() {
        ManagedWorld w =
                ManagedWorld.created(WorldName.of("w"), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH);
        WorldSettings updated = w.settings().with(WorldProperties.PVP, false);
        ManagedWorld next = w.withSettings(updated);
        assertThat(next.settings().get(WorldProperties.PVP)).isFalse();
        assertThat(w.settings().raw()).isEmpty(); // original unchanged
        assertThat(next.name()).isEqualTo(w.name()); // other fields preserved
    }
}
