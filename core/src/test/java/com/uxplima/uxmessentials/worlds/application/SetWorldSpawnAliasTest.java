package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

class SetWorldSpawnAliasTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final SetWorldSpawn setSpawn =
            new SetWorldSpawn(repo, TestSupport.notifier(), e -> {}, TestSupport.inlineScheduler());
    private final SetWorldAlias setAlias =
            new SetWorldAlias(repo, TestSupport.notifier(), e -> {}, TestSupport.inlineScheduler());
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Op");

    private void register(String n) {
        repo.save(ManagedWorld.created(WorldName.of(n), WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
    }

    @Test
    void setsSpawnFromPosition() {
        register("w");
        var pos = new Position(new WorldRef(UUID.randomUUID(), "w"), 10.5, 64, 20.5, 90f, 0f);
        assertThat(setSpawn.set(who, WorldName.of("w"), pos).isOk()).isTrue();
        assertThat(repo.find(WorldName.of("w")).orElseThrow().settings().spawn())
                .isPresent();
    }

    @Test
    void setsAlias() {
        register("w");
        assertThat(setAlias.set(who, WorldName.of("w"), Optional.of("Spawn")).isOk())
                .isTrue();
        assertThat(repo.find(WorldName.of("w")).orElseThrow().alias()).contains("Spawn");
    }

    @Test
    void bothRejectUnknownWorld() {
        var pos = new Position(new WorldRef(UUID.randomUUID(), "ghost"), 0, 0, 0, 0f, 0f);
        assertThat(setSpawn.set(who, WorldName.of("ghost"), pos).errorOrThrow().name())
                .isEqualTo("NOT_FOUND");
        assertThat(setAlias.set(who, WorldName.of("ghost"), Optional.of("x"))
                        .errorOrThrow()
                        .name())
                .isEqualTo("NOT_FOUND");
    }
}
