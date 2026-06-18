package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetHologramGrowUpTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private SetHologramGrowUp setGrowUp;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        setGrowUp = new SetHologramGrowUp(
                repository, view, new HologramNotifier(new HologramTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void enablesGrowUpSavesAndReRenders() {
        repository.save(hologram("spawn"));

        Result<Unit, HologramError> result = setGrowUp.set(actor, HologramName.of("spawn"), true);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().growUp())
                .isTrue();
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_GROWUP_ENABLED.key());
    }

    @Test
    void disablingGrowUpUsesTheDownwardMessage() {
        repository.save(hologram("spawn").withGrowUp(true));

        Result<Unit, HologramError> result = setGrowUp.set(actor, HologramName.of("spawn"), false);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().growUp())
                .isFalse();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_GROWUP_DISABLED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, HologramError> result = setGrowUp.set(actor, HologramName.of("ghost"), true);

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }

    private Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine("line")), Instant.EPOCH);
    }
}
