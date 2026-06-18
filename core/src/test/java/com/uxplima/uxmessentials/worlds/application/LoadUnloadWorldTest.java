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
import com.uxplima.uxmessentials.worlds.domain.event.WorldLoaded;
import com.uxplima.uxmessentials.worlds.domain.event.WorldUnloaded;
import org.junit.jupiter.api.Test;

class LoadUnloadWorldTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final List<DomainEvent> events = new ArrayList<>();
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Op");
    private final LoadWorld loadWorld = new LoadWorld(repo, engine, TestSupport.notifier(), events::add);
    private final UnloadWorld unloadWorld = new UnloadWorld(engine, TestSupport.notifier(), events::add, () -> true);

    private void register(String name) {
        repo.save(ManagedWorld.created(WorldName.of(name), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        engine.onDisk.add(name);
    }

    @Test
    void loadsARegisteredUnloadedWorld() {
        register("creative");
        var result = loadWorld.load(who, WorldName.of("creative"));
        assertThat(result.isOk()).isTrue();
        assertThat(engine.isLoaded(WorldName.of("creative"))).isTrue();
        assertThat(events).first().isInstanceOf(WorldLoaded.class);
    }

    @Test
    void loadRejectsAnAlreadyLoadedWorld() {
        register("creative");
        engine.loaded.add("creative");
        assertThat(loadWorld.load(who, WorldName.of("creative")).errorOrThrow().name())
                .isEqualTo("ALREADY_LOADED");
    }

    @Test
    void unloadsALoadedWorld() {
        register("creative");
        engine.loaded.add("creative");
        var result = unloadWorld.unload(who, WorldName.of("creative"), true);
        assertThat(result.isOk()).isTrue();
        assertThat(engine.isLoaded(WorldName.of("creative"))).isFalse();
        assertThat(events).anyMatch(WorldUnloaded.class::isInstance);
    }

    @Test
    void unloadRejectsTheProtectedDefaultWorld() {
        register("world");
        engine.loaded.add("world");
        engine.defaultWorld = WorldName.of("world");
        assertThat(unloadWorld
                        .unload(who, WorldName.of("world"), true)
                        .errorOrThrow()
                        .name())
                .isEqualTo("IS_PROTECTED");
    }

    @Test
    void unloadRejectsWhenPlayersPresent() {
        register("creative");
        engine.loaded.add("creative");
        engine.playerCount = 3;
        assertThat(unloadWorld
                        .unload(who, WorldName.of("creative"), true)
                        .errorOrThrow()
                        .name())
                .isEqualTo("PLAYERS_PRESENT");
    }
}
