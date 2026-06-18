package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.event.WorldUnregistered;
import org.junit.jupiter.api.Test;

class UnregisterWorldTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final List<DomainEvent> events = new ArrayList<>();
    private final UnregisterWorld unregister = new UnregisterWorld(repo, TestSupport.notifier(), events::add);
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Op");

    @Test
    void dropsTheRowAndKeepsNothingElse() {
        repo.save(ManagedWorld.created(
                WorldName.of("creative"), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        var result = unregister.unregister(who, WorldName.of("creative"));
        assertThat(result.isOk()).isTrue();
        assertThat(repo.exists(WorldName.of("creative"))).isFalse();
        assertThat(events).first().isInstanceOf(WorldUnregistered.class);
    }

    @Test
    void rejectsUnknownWorld() {
        assertThat(unregister
                        .unregister(who, WorldName.of("ghost"))
                        .errorOrThrow()
                        .name())
                .isEqualTo("NOT_FOUND");
    }
}
