package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.event.WorldAdopted;
import org.junit.jupiter.api.Test;

class ReconcileWorldsOnEnableTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final List<DomainEvent> events = new ArrayList<>();

    private ReconcileWorldsOnEnable reconcile(boolean adopt, boolean autoLoad) {
        return new ReconcileWorldsOnEnable(
                repo, engine, events::add, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), () -> adopt, () -> autoLoad);
    }

    @Test
    void adoptsLoadedWorldsNotYetRegistered() {
        engine.loaded.add("world");
        engine.onDisk.add("world");
        engine.scanResult = Optional.of(new WorldEngine.DetectedWorld(WorldEnvironment.NORMAL, Optional.empty()));

        reconcile(true, true).run();

        assertThat(repo.exists(WorldName.of("world"))).isTrue();
        assertThat(repo.find(WorldName.of("world")).orElseThrow().adopted()).isTrue();
        assertThat(events).anyMatch(WorldAdopted.class::isInstance);
    }

    @Test
    void autoLoadsRegisteredUnloadedWorlds() {
        repo.save(ManagedWorld.created(
                WorldName.of("creative"), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        engine.onDisk.add("creative");

        reconcile(true, true).run();

        assertThat(engine.isLoaded(WorldName.of("creative"))).isTrue();
    }

    @Test
    void doesNotAutoLoadWhenDisabled() {
        repo.save(ManagedWorld.created(
                WorldName.of("creative"), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        engine.onDisk.add("creative");
        reconcile(true, false).run();
        assertThat(engine.isLoaded(WorldName.of("creative"))).isFalse();
    }
}
