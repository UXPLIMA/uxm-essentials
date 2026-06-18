package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.worlds.domain.PendingDeletion;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.Test;

class InMemoryPendingDeletionRegistryTest {

    private final InMemoryPendingDeletionRegistry registry = new InMemoryPendingDeletionRegistry();
    private final UUID who = UUID.randomUUID();

    @Test
    void takeConsumesOnlyAMatchingStagedDeletion() {
        registry.stage(new PendingDeletion(WorldName.of("creative"), who, Instant.EPOCH));
        assertThat(registry.take(WorldName.of("other"), who)).isEmpty(); // wrong world
        assertThat(registry.take(WorldName.of("creative"), UUID.randomUUID())).isEmpty(); // wrong requester
        assertThat(registry.take(WorldName.of("creative"), who)).isPresent();
        assertThat(registry.take(WorldName.of("creative"), who)).isEmpty(); // consumed
    }

    @Test
    void stagingAgainReplacesThePriorRequest() {
        registry.stage(new PendingDeletion(WorldName.of("a"), who, Instant.EPOCH));
        registry.stage(new PendingDeletion(WorldName.of("b"), who, Instant.EPOCH));
        assertThat(registry.peek(who).orElseThrow().name()).isEqualTo(WorldName.of("b"));
    }
}
