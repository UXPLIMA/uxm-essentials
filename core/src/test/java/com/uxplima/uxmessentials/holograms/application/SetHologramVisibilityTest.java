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
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetHologramVisibilityTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private SetHologramVisibility visibility;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        visibility = new SetHologramVisibility(
                repository, view, new HologramNotifier(new HologramTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void gatesAHologramBehindAPermissionNodeSavesAndReRenders() {
        repository.save(hologram("spawn"));

        Result<Unit, HologramError> result = visibility.setMode(
                actor, HologramName.of("spawn"), current -> current.toPermission("uxmessentials.hologram.see.vip"));

        assertThat(result.isOk()).isTrue();
        Visibility updated =
                repository.find(HologramName.of("spawn")).orElseThrow().visibility();
        assertThat(updated.mode()).isEqualTo(Visibility.Mode.PERMISSION);
        assertThat(updated.permission()).isEqualTo("uxmessentials.hologram.see.vip");
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_VISIBILITY_SET.key());
    }

    @Test
    void openingBackToEveryoneDropsTheNode() {
        repository.save(hologram("spawn").withVisibility(Visibility.restrictedTo("node.x")));

        visibility.setMode(actor, HologramName.of("spawn"), Visibility::toEveryone);

        Visibility updated =
                repository.find(HologramName.of("spawn")).orElseThrow().visibility();
        assertThat(updated.mode()).isEqualTo(Visibility.Mode.ALL);
        assertThat(updated.permission()).isNull();
    }

    @Test
    void setsAndClampsTheVisibilityDistance() {
        repository.save(hologram("spawn"));

        visibility.setDistance(actor, HologramName.of("spawn"), 10_000);

        Visibility updated =
                repository.find(HologramName.of("spawn")).orElseThrow().visibility();
        assertThat(updated.distance()).isEqualTo(Visibility.MAX_DISTANCE);
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_VISIBILITY_DISTANCE_SET.key());
    }

    @Test
    void distanceChangeKeepsThePermissionMode() {
        repository.save(hologram("spawn").withVisibility(Visibility.restrictedTo("node.x")));

        visibility.setDistance(actor, HologramName.of("spawn"), 48);

        Visibility updated =
                repository.find(HologramName.of("spawn")).orElseThrow().visibility();
        assertThat(updated.mode()).isEqualTo(Visibility.Mode.PERMISSION);
        assertThat(updated.permission()).isEqualTo("node.x");
        assertThat(updated.distance()).isEqualTo(48);
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, HologramError> result =
                visibility.setMode(actor, HologramName.of("ghost"), Visibility::toEveryone);

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }

    private Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine("line")), Instant.EPOCH);
    }
}
