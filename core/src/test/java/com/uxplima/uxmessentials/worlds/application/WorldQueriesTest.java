package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

class WorldQueriesTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final FakeWorldEngine engine = new FakeWorldEngine();

    @Test
    void listReportsLoadedStatePerWorld() {
        repo.save(ManagedWorld.created(WorldName.of("a"), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        repo.save(ManagedWorld.created(WorldName.of("b"), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        engine.loaded.add("a");

        var entries = new ListWorlds(repo, engine).all();

        assertThat(entries).hasSize(2);
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.name()).isEqualTo(WorldName.of("a"));
            assertThat(e.loaded()).isTrue();
        });
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.name()).isEqualTo(WorldName.of("b"));
            assertThat(e.loaded()).isFalse();
        });
    }

    @Test
    void infoFindsAManagedWorld() {
        repo.save(ManagedWorld.created(WorldName.of("a"), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        assertThat(new WorldInfo(repo).find(WorldName.of("a"))).isPresent();
        assertThat(new WorldInfo(repo).find(WorldName.of("ghost"))).isEmpty();
    }
}
