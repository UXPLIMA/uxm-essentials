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
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AddHologramPageTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private AddHologramPage add;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        add = new AddHologramPage(repository, view, new Notifier(new HologramTestSupport.KeyMessages(), sink));
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void appendsAPageSavesAndReRenders() {
        repository.save(text("spawn", "p0"));

        Result<Unit, HologramError> result = add.add(actor, HologramName.of("spawn"), List.of(new HologramLine("p1")));

        assertThat(result.isOk()).isTrue();
        Hologram updated = repository.find(HologramName.of("spawn")).orElseThrow();
        assertThat(updated.isMultiPage()).isTrue();
        assertThat(updated.pageCount()).isEqualTo(2);
        assertThat(updated.pageLines(1)).map(HologramLine::value).containsExactly("p1");
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_PAGE_ADDED.key());
    }

    @Test
    void rejectsAnUnknownName() {
        Result<Unit, HologramError> result = add.add(actor, HologramName.of("ghost"), List.of(new HologramLine("x")));

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }

    @Test
    void rejectsANonTextHologram() {
        repository.save(
                Hologram.createItem(HologramName.of("shop"), Position.of(WORLD, 0, 64, 0), "DIAMOND", Instant.EPOCH));

        Result<Unit, HologramError> result = add.add(actor, HologramName.of("shop"), List.of(new HologramLine("p1")));

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_TEXT_HOLOGRAM);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_PAGE_NOT_TEXT.key());
    }

    private Hologram text(String name, String line) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine(line)), Instant.EPOCH);
    }
}
