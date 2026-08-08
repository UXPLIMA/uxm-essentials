package com.uxplima.uxmessentials.holograms.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.CapturingSink;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.FakeHologramRepository;
import com.uxplima.uxmessentials.holograms.application.HologramTestSupport.RecordingView;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
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

class HologramNpcLinkTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private FakeHologramRepository repository;
    private RecordingView view;
    private CapturingSink sink;
    private FakeNpcLocator npcs;
    private LinkHologramToNpc link;
    private UnlinkHologramFromNpc unlink;
    private PlayerRef actor;

    @BeforeEach
    void setUp() {
        repository = new FakeHologramRepository();
        view = new RecordingView();
        sink = new CapturingSink();
        npcs = new FakeNpcLocator();
        Notifier notifier = new Notifier(new HologramTestSupport.KeyMessages(), sink);
        link = new LinkHologramToNpc(repository, view, notifier, npcs);
        unlink = new UnlinkHologramFromNpc(repository, view, notifier);
        actor = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @Test
    void linksAHologramToAnExistingNpcSavesAndReRenders() {
        repository.save(hologram("spawn"));
        npcs.put("guide", Position.of(WORLD, 5, 70, 5));

        Result<Unit, HologramError> result = link.link(actor, HologramName.of("spawn"), "guide");

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().linkedNpcName())
                .isEqualTo("guide");
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_LINKED.key());
    }

    @Test
    void rejectsLinkingToAnUnknownNpcAndWritesNoLink() {
        repository.save(hologram("spawn"));

        Result<Unit, HologramError> result = link.link(actor, HologramName.of("spawn"), "ghost");

        assertThat(result.isOk()).isFalse();
        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().isLinked())
                .isFalse();
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NPC_NOT_FOUND.key());
    }

    @Test
    void rejectsLinkingAnUnknownHologram() {
        npcs.put("guide", Position.of(WORLD, 5, 70, 5));

        Result<Unit, HologramError> result = link.link(actor, HologramName.of("ghost"), "guide");

        assertThat(result.errorOrThrow()).isEqualTo(HologramError.NOT_FOUND);
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_FOUND.key());
    }

    @Test
    void unlinkRestoresTheOwnAnchorSavesAndReRenders() {
        npcs.put("guide", Position.of(WORLD, 5, 70, 5));
        repository.save(hologram("spawn"));
        link.link(actor, HologramName.of("spawn"), "guide");
        view.rendered.clear();

        Result<Unit, HologramError> result = unlink.unlink(actor, HologramName.of("spawn"));

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().isLinked())
                .isFalse();
        assertThat(view.rendered).hasSize(1);
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_UNLINKED.key());
    }

    @Test
    void unlinkingAnAlreadyUnlinkedHologramTellsTheOperatorAndDoesNotReRender() {
        repository.save(hologram("spawn"));

        Result<Unit, HologramError> result = unlink.unlink(actor, HologramName.of("spawn"));

        assertThat(result.isOk()).isTrue();
        assertThat(view.rendered).isEmpty();
        assertThat(sink.textFor(actor)).contains(HologramsMessageKey.HOLOGRAM_NOT_LINKED.key());
    }

    private Hologram hologram(String name) {
        return Hologram.create(
                HologramName.of(name),
                Position.of(WORLD, 1, 64, 1),
                List.of(new HologramLine("line")),
                Instant.ofEpochMilli(1_000));
    }

    /** An in-memory {@link LinkedNpcLocator} for the use-case tests, keyed by NPC name. */
    private static final class FakeNpcLocator implements LinkedNpcLocator {
        private final Map<String, Position> positions = new java.util.HashMap<>();

        void put(String name, Position position) {
            positions.put(name, position);
        }

        @Override
        public Optional<Position> locate(String npcName) {
            return Optional.ofNullable(positions.get(npcName));
        }
    }
}
