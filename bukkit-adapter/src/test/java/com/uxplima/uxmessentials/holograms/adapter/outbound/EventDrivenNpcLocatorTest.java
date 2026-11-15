package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.event.NpcMoved;
import com.uxplima.uxmessentials.npc.domain.event.NpcRescoped;
import com.uxplima.uxmessentials.shared.domain.LocalServer;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/** A hologram riding an NPC follows the NPC's scope: off a server the NPC left, back on one it returned to. */
class EventDrivenNpcLocatorTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final LocalServer SURVIVAL = new LocalServer("survival", List.of());

    private final List<String> reanchored = new ArrayList<>();
    private final List<String> rescoped = new ArrayList<>();

    private static Npc npc(String name) {
        return Npc.create(NpcName.of(name), new Position(WORLD, 0, 64, 0, 0, 0), null, Instant.EPOCH);
    }

    @Test
    void anNpcScopedToAnotherServerIsNotShownHereAndAnUnscopedOneIs() {
        EventDrivenNpcLocator locator = new EventDrivenNpcLocator(
                new Stored(List.of(npc("guide").withScope(ServerScope.server("lobby-1")), npc("greeter"))),
                SURVIVAL,
                reanchored::add,
                rescoped::add);

        assertThat(locator.shownHere("guide")).isFalse();
        assertThat(locator.shownHere("greeter")).isTrue();
        assertThat(locator.shownHere("nobody")).isTrue();
    }

    @Test
    void anNpcLeavingOrReturningToThisServerRescopesWhatRidesItAndAMoveOnlyReanchors() {
        EventDrivenNpcLocator locator =
                new EventDrivenNpcLocator(new Stored(List.of(npc("guide"))), SURVIVAL, reanchored::add, rescoped::add);

        locator.accept(new NpcRescoped(NpcName.of("guide"), ServerScope.server("lobby-1")));
        assertThat(locator.shownHere("guide")).isFalse();
        locator.accept(new NpcRescoped(NpcName.of("guide"), null));
        assertThat(locator.shownHere("guide")).isTrue();
        locator.accept(new NpcMoved(NpcName.of("guide"), new Position(WORLD, 9, 64, 0, 0, 0)));

        assertThat(rescoped).containsExactly("guide", "guide");
        assertThat(reanchored).containsExactly("guide");
        assertThat(locator.locate("guide")).contains(new Position(WORLD, 9, 64, 0, 0, 0));
    }

    @Test
    void anNpcRescopedOnAnotherServerIsTakenInThroughARefresh() {
        EventDrivenNpcLocator locator =
                new EventDrivenNpcLocator(new Stored(List.of(npc("guide"))), SURVIVAL, reanchored::add, rescoped::add);

        locator.refresh("guide", Optional.of(npc("guide").withScope(ServerScope.set("lobby"))));

        assertThat(locator.shownHere("guide")).isFalse();
        assertThat(rescoped).containsExactly("guide");
    }

    private record Stored(List<Npc> npcs) implements NpcRepository {
        @Override
        public Optional<Npc> find(NpcName name) {
            return npcs.stream().filter(npc -> npc.name().equals(name)).findFirst();
        }

        @Override
        public List<Npc> all() {
            return npcs;
        }

        @Override
        public boolean exists(NpcName name) {
            return find(name).isPresent();
        }

        @Override
        public void save(Npc npc) {
            throw new UnsupportedOperationException("the locator only reads");
        }

        @Override
        public void delete(NpcName name) {
            throw new UnsupportedOperationException("the locator only reads");
        }
    }
}
