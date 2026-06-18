package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import com.uxplima.uxmessentials.worlds.domain.event.WorldCreated;
import org.junit.jupiter.api.Test;

class CreateWorldTest {

    private final FakeWorldRepository repo = new FakeWorldRepository();
    private final FakeWorldEngine engine = new FakeWorldEngine();
    private final List<DomainEvent> events = new ArrayList<>();
    private final DomainEventPublisher publisher = events::add;
    private final PlayerRef creator = new PlayerRef(UUID.randomUUID(), "Op");
    private final CreateWorld createWorld = new CreateWorld(
            repo, engine, TestSupport.notifier(), publisher, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    @Test
    void createsPersistsAndPublishes() {
        var result = createWorld.create(creator, WorldName.of("creative"), WorldSpec.normal(), true);

        assertThat(result.isOk()).isTrue();
        assertThat(repo.exists(WorldName.of("creative"))).isTrue();
        assertThat(engine.isLoaded(WorldName.of("creative"))).isTrue();
        assertThat(events).hasSize(1).first().isInstanceOf(WorldCreated.class);
    }

    @Test
    void rejectsADuplicateName() {
        createWorld.create(creator, WorldName.of("creative"), WorldSpec.normal(), true);
        events.clear();

        var result = createWorld.create(creator, WorldName.of("creative"), WorldSpec.normal(), true);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow().name()).isEqualTo("ALREADY_EXISTS");
        assertThat(events).isEmpty();
    }

    @Test
    void rejectsWhenTheFolderAlreadyExistsOnDisk() {
        engine.onDisk.add("oldworld");
        var result = createWorld.create(creator, WorldName.of("oldworld"), WorldSpec.normal(), true);
        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow().name()).isEqualTo("ALREADY_EXISTS");
    }
}
