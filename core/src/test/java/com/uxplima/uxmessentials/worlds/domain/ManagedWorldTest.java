package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class ManagedWorldTest {

    @Test
    void createdWorldHasNoUidUntilLoaded() {
        ManagedWorld w = ManagedWorld.created(
                WorldName.of("creative"),
                WorldSpec.normal(),
                true,
                Optional.of(UUID.randomUUID()),
                Instant.ofEpochMilli(1000));
        assertThat(w.name()).isEqualTo(WorldName.of("creative"));
        assertThat(w.autoLoad()).isTrue();
        assertThat(w.adopted()).isFalse();
        assertThat(w.knownUid()).isEmpty();
    }

    @Test
    void adoptedWorldCarriesItsUidAndIsFlaggedAdopted() {
        UUID uid = UUID.randomUUID();
        ManagedWorld w = ManagedWorld.adopted(WorldName.of("world"), WorldSpec.normal(), uid, Instant.ofEpochMilli(0));
        assertThat(w.adopted()).isTrue();
        assertThat(w.knownUid()).hasValue(uid);
        assertThat(w.autoLoad()).isTrue();
    }

    @Test
    void copyMethodsReturnNewInstances() {
        ManagedWorld w =
                ManagedWorld.created(WorldName.of("w"), WorldSpec.normal(), false, Optional.empty(), Instant.EPOCH);
        UUID uid = UUID.randomUUID();
        assertThat(w.withAutoLoad(true).autoLoad()).isTrue();
        assertThat(w.withAlias(Optional.of("Spawn")).alias()).hasValue("Spawn");
        assertThat(w.withKnownUid(uid).knownUid()).hasValue(uid);
        assertThat(w.autoLoad()).isFalse(); // original unchanged
    }
}
