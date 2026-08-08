package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ListHologramsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private CapturingSink sink;
    private ListHolograms list;
    private PlayerRef viewer;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        sink = new CapturingSink();
        list = new ListHolograms(repository, new Notifier(new HologramTestSupport.KeyMessages(), sink));
        viewer = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void emptyListSendsTheEmptyNotice() {
        List<Hologram> shown = list.list(viewer);

        assertThat(shown).isEmpty();
        assertThat(sink.textFor(viewer)).contains(HologramsMessageKey.HOLOGRAM_LIST_EMPTY.key());
    }

    @Test
    void listsEveryHologramInCreationOrderWithHeaderAndEntries() {
        repository.save(hologram("first"));
        repository.save(hologram("second"));

        List<Hologram> shown = list.list(viewer);

        assertThat(shown).map(h -> h.name().value()).containsExactly("first", "second");
        assertThat(sink.textFor(viewer)).contains(HologramsMessageKey.HOLOGRAM_LIST_HEADER.key());
        assertThat(sink.textFor(viewer))
                .filteredOn(t -> t.equals(HologramsMessageKey.HOLOGRAM_LIST_ENTRY.key()))
                .hasSize(2);
    }

    private Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine("line")), Instant.EPOCH);
    }
}
