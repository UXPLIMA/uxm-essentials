package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.event.NpcCreated;
import com.uxplima.uxmessentials.npc.domain.event.NpcDeleted;
import com.uxplima.uxmessentials.npc.domain.event.NpcMoved;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage of the cross-context NPC locator: it seeds from the npc repository, stays current off the
 * domain-event bus, and fires the re-anchor callback when a linked NPC moves or is removed.
 */
class EventDrivenNpcLocatorTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef ACTOR = new PlayerRef(UUID.randomUUID(), "Operator");

    private FakeNpcRepository repository;
    private List<String> reanchored;
    private EventDrivenNpcLocator locator;

    @BeforeEach
    void setUp() {
        repository = new FakeNpcRepository();
        reanchored = new ArrayList<>();
        repository.save(Npc.create(NpcName.of("guide"), Position.of(WORLD, 1, 64, 1), null, Instant.ofEpochMilli(1)));
        locator = new EventDrivenNpcLocator(repository, reanchored::add);
    }

    @Test
    void seedsFromTheRepository() {
        assertThat(locator.locate("guide")).contains(Position.of(WORLD, 1, 64, 1));
        assertThat(locator.locate("absent")).isEmpty();
    }

    @Test
    void aCreatedEventAddsToTheIndexWithoutReanchoring() {
        Position at = Position.of(WORLD, 5, 70, 5);

        locator.accept(new NpcCreated(NpcName.of("shop"), ACTOR, at));

        assertThat(locator.locate("shop")).contains(at);
        assertThat(reanchored).isEmpty();
    }

    @Test
    void aMovedEventUpdatesTheIndexAndReanchors() {
        Position to = Position.of(WORLD, 9, 72, 9);

        locator.accept(new NpcMoved(NpcName.of("guide"), to));

        assertThat(locator.locate("guide")).contains(to);
        assertThat(reanchored).containsExactly("guide");
    }

    @Test
    void aDeletedEventClearsTheIndexAndReanchors() {
        locator.accept(new NpcDeleted(NpcName.of("guide"), ACTOR));

        assertThat(locator.locate("guide")).isEmpty();
        assertThat(reanchored).containsExactly("guide");
    }

    /** A minimal in-memory {@link NpcRepository} for the locator seed. */
    private static final class FakeNpcRepository implements NpcRepository {
        private final Map<String, Npc> byName = new LinkedHashMap<>();

        @Override
        public Optional<Npc> find(NpcName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Npc> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(NpcName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Npc npc) {
            byName.put(npc.name().value(), npc);
        }

        @Override
        public void delete(NpcName name) {
            byName.remove(name.value());
        }
    }
}
