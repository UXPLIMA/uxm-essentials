package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingView;
import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
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

class SetHologramAppearanceTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private SetHologramAppearance appearance;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        appearance = new SetHologramAppearance(
                repository, view, new HologramNotifier(new HologramTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void appliesTheTransitionSavesAndReRenders() {
        repository.save(hologram("spawn"));

        Result<Unit, HologramError> result = appearance.apply(
                actor,
                HologramName.of("spawn"),
                current -> current.withBillboard(Billboard.FIXED).withScale(2.0f));

        assertThat(result.isOk()).isTrue();
        Hologram updated = repository.find(HologramName.of("spawn")).orElseThrow();
        assertThat(updated.appearance().billboard()).isEqualTo(Billboard.FIXED);
        assertThat(updated.appearance().scale()).isEqualTo(2.0f);
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_APPEARANCE_SET.key());
    }

    @Test
    void leavesEveryOtherFieldUntouched() {
        repository.save(hologram("spawn"));

        appearance.apply(actor, HologramName.of("spawn"), current -> current.withBackgroundArgb(0x80112233));

        Appearance updated =
                repository.find(HologramName.of("spawn")).orElseThrow().appearance();
        assertThat(updated.backgroundArgb()).isEqualTo(0x80112233);
        assertThat(updated.billboard()).isEqualTo(Billboard.CENTER);
        assertThat(updated.lineWidth()).isEqualTo(Appearance.defaults().lineWidth());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, HologramError> result =
                appearance.apply(actor, HologramName.of("ghost"), current -> current.withScale(2.0f));

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }

    private Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine("line")), Instant.EPOCH);
    }
}
