package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingView;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.ViewerChange;
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

class ManageHologramViewerTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private ManageHologramViewer viewers;
    private PlayerRef actor;
    private PlayerRef target;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        viewers = new ManageHologramViewer(
                repository, view, new HologramNotifier(new HologramTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
        target = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @Test
    void showAddsTheViewerPersistsAndAppliesItLive() {
        repository.save(manual("spawn"));

        Result<Unit, HologramError> result = viewers.show(actor, HologramName.of("spawn"), target);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.manualViewers(HologramName.of("spawn"))).containsExactly(target.uuid());
        assertThat(view.viewerChanges).containsExactly(new ViewerChange(HologramName.of("spawn"), target.uuid(), true));
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_SHOWN_TO.key());
    }

    @Test
    void hideRemovesTheViewerPersistsAndAppliesItLive() {
        repository.save(manual("spawn"));
        repository.showTo(HologramName.of("spawn"), target.uuid());

        Result<Unit, HologramError> result = viewers.hide(actor, HologramName.of("spawn"), target);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.manualViewers(HologramName.of("spawn"))).isEmpty();
        assertThat(view.viewerChanges)
                .containsExactly(new ViewerChange(HologramName.of("spawn"), target.uuid(), false));
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_HIDDEN_FROM.key());
    }

    @Test
    void showIsIdempotentAndFlagsAnAlreadyShownViewer() {
        repository.save(manual("spawn"));
        repository.showTo(HologramName.of("spawn"), target.uuid());

        Result<Unit, HologramError> result = viewers.show(actor, HologramName.of("spawn"), target);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.manualViewers(HologramName.of("spawn"))).containsExactly(target.uuid());
        // No second live apply when the viewer was already in the set.
        assertThat(view.viewerChanges).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_ALREADY_SHOWN.key());
    }

    @Test
    void hideFlagsAViewerThatWasNotShown() {
        repository.save(manual("spawn"));

        Result<Unit, HologramError> result = viewers.hide(actor, HologramName.of("spawn"), target);

        assertThat(result.isOk()).isTrue();
        assertThat(view.viewerChanges).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_SHOWN.key());
    }

    @Test
    void showRejectsAnUnknownName() {
        Result<Unit, HologramError> result = viewers.show(actor, HologramName.of("ghost"), target);

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(view.viewerChanges).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }

    @Test
    void hideRejectsAnUnknownName() {
        Result<Unit, HologramError> result = viewers.hide(actor, HologramName.of("ghost"), target);

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(view.viewerChanges).isEmpty();
    }

    @Test
    void showWorksRegardlessOfMode() {
        // The set can be managed before switching to MANUAL; it only has a visible effect once MANUAL.
        repository.save(hologram("spawn"));

        Result<Unit, HologramError> result = viewers.show(actor, HologramName.of("spawn"), target);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.manualViewers(HologramName.of("spawn"))).containsExactly(target.uuid());
    }

    private Hologram manual(String name) {
        return hologram(name).withVisibility(Visibility.manual());
    }

    private Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine("line")), Instant.EPOCH);
    }
}
