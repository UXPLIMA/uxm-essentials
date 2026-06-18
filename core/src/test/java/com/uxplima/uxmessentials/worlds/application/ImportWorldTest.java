package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.application.port.WorldEngine;
import com.uxplima.uxmessentials.worlds.domain.WorldEnvironment;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.event.WorldImported;
import org.junit.jupiter.api.Test;

class ImportWorldTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final List<DomainEvent> events = new ArrayList<>();
    private final ImportWorld importWorld = new ImportWorld(
            repo,
            engine,
            TestSupport.notifier(),
            events::add,
            TestSupport.inlineScheduler(),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    private final PlayerRef who = new PlayerRef(UUID.randomUUID(), "Op");

    @Test
    void importsAnExistingFolder() {
        engine.onDisk.add("oldworld");
        engine.scanResult = Optional.of(new WorldEngine.DetectedWorld(WorldEnvironment.NORMAL, Optional.of(7L)));

        var result = importWorld.importWorld(who, WorldName.of("oldworld"), WorldEnvironment.NORMAL, Optional.empty());

        assertThat(result.isOk()).isTrue();
        assertThat(repo.exists(WorldName.of("oldworld"))).isTrue();
        assertThat(engine.isLoaded(WorldName.of("oldworld"))).isTrue();
        assertThat(events).first().isInstanceOf(WorldImported.class);
    }

    @Test
    void rejectsWhenAlreadyRegistered() {
        engine.onDisk.add("oldworld");
        engine.scanResult = Optional.of(new WorldEngine.DetectedWorld(WorldEnvironment.NORMAL, Optional.empty()));
        importWorld.importWorld(who, WorldName.of("oldworld"), WorldEnvironment.NORMAL, Optional.empty());

        var result = importWorld.importWorld(who, WorldName.of("oldworld"), WorldEnvironment.NORMAL, Optional.empty());
        assertThat(result.errorOrThrow().name()).isEqualTo("ALREADY_EXISTS");
    }

    @Test
    void rejectsWhenFolderIsMissingOrNotAWorld() {
        var missing = importWorld.importWorld(who, WorldName.of("ghost"), WorldEnvironment.NORMAL, Optional.empty());
        assertThat(missing.errorOrThrow().name()).isEqualTo("FOLDER_MISSING");

        engine.onDisk.add("notaworld");
        engine.scanResult = Optional.empty();
        var notWorld =
                importWorld.importWorld(who, WorldName.of("notaworld"), WorldEnvironment.NORMAL, Optional.empty());
        assertThat(notWorld.errorOrThrow().name()).isEqualTo("NOT_A_WORLD_FOLDER");
    }
}
