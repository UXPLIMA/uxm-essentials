package com.uxplima.uxmessentials.worlds.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.worlds.domain.BackupId;
import com.uxplima.uxmessentials.worlds.domain.PendingRestore;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.junit.jupiter.api.Test;

class InMemoryPendingRestoreRegistryTest {

    private final InMemoryPendingRestoreRegistry registry = new InMemoryPendingRestoreRegistry();
    private final UUID who = UUID.randomUUID();
    private final BackupId id = BackupId.of("snap-1");

    @Test
    void takeConsumesOnlyAMatchingStagedRestore() {
        registry.stage(new PendingRestore(WorldName.of("creative"), id, who));
        assertThat(registry.take(WorldName.of("other"), who)).isEmpty(); // wrong world
        assertThat(registry.take(WorldName.of("creative"), UUID.randomUUID())).isEmpty(); // wrong requester
        assertThat(registry.take(WorldName.of("creative"), who)).isPresent();
        assertThat(registry.take(WorldName.of("creative"), who)).isEmpty(); // consumed
    }

    @Test
    void peekReturnsTheStagedRestoreWithoutConsuming() {
        registry.stage(new PendingRestore(WorldName.of("creative"), id, who));
        assertThat(registry.peek(who)).isPresent();
        assertThat(registry.peek(who)).isPresent();
    }

    @Test
    void stagingAgainReplacesThePriorRequest() {
        registry.stage(new PendingRestore(WorldName.of("a"), id, who));
        registry.stage(new PendingRestore(WorldName.of("b"), id, who));
        assertThat(registry.peek(who).orElseThrow().world()).isEqualTo(WorldName.of("b"));
    }
}
