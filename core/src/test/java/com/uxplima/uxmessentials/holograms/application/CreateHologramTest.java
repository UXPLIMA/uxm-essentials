package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingEvents;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingView;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.event.HologramCreated;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateHologramTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 1, 64, 1);

    private FakeHologramRepository repository;
    private RecordingView view;
    private RecordingEvents events;
    private CapturingSink sink;
    private CreateHologram create;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        events = new RecordingEvents();
        sink = new CapturingSink();
        create = new CreateHologram(
                repository,
                view,
                new HologramNotifier(new HologramTestSupport.KeyMessages(), sink),
                events,
                Clock.fixed(Instant.ofEpochMilli(5_000), ZoneOffset.UTC));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void createsStoresRendersPublishesAndNotifies() {
        Result<Unit, HologramError> result = create.create(actor, HologramName.of("spawn"), AT, new HologramLine("Hi"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.exists(HologramName.of("spawn"))).isTrue();
        assertThat(view.rendered).hasSize(1);
        assertThat(events.published).hasSize(1).first().isInstanceOf(HologramCreated.class);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_CREATED.key());
    }

    @Test
    void rejectsATakenName() {
        create.create(actor, HologramName.of("spawn"), AT, new HologramLine("Hi"));

        Result<Unit, HologramError> again =
                create.create(actor, HologramName.of("Spawn"), AT, new HologramLine("Other"));

        assertThat(again.errorOrThrow()).isEqualTo(HologramError.NAME_TAKEN);
        assertThat(repository.all()).hasSize(1);
        assertThat(view.rendered).hasSize(1); // no second render
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NAME_TAKEN.key());
    }

    @Test
    void theCreatedHologramCarriesTheClockInstantAndTheFirstLine() {
        create.create(actor, HologramName.of("spawn"), AT, new HologramLine("Welcome"));

        var stored = repository.find(HologramName.of("spawn")).orElseThrow();
        assertThat(stored.createdAt()).isEqualTo(Instant.ofEpochMilli(5_000));
        assertThat(stored.lines()).map(HologramLine::value).isEqualTo(List.of("Welcome"));
    }
}
