package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingEvents;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.event.HologramDeleted;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeleteHologramTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private RecordingView view;
    private RecordingEvents events;
    private CapturingSink sink;
    private DeleteHologram delete;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        events = new RecordingEvents();
        sink = new CapturingSink();
        delete =
                new DeleteHologram(repository, view, new Notifier(new HologramTestSupport.KeyMessages(), sink), events);
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void deletesDespawnsPublishesAndNotifies() {
        repository.save(hologram("spawn"));

        Result<Unit, HologramError> result = delete.delete(actor, HologramName.of("spawn"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.exists(HologramName.of("spawn"))).isFalse();
        assertThat(view.despawned).containsExactly(HologramName.of("spawn"));
        assertThat(events.published).hasSize(1).first().isInstanceOf(HologramDeleted.class);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_DELETED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, HologramError> result = delete.delete(actor, HologramName.of("ghost"));

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(view.despawned).isEmpty();
        assertThat(events.published).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }

    private Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine("line")), Instant.EPOCH);
    }
}
