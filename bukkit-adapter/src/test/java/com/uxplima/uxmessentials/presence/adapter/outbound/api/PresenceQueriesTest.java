package com.uxplima.uxmessentials.presence.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.api.view.UxmPresence;
import com.uxplima.uxmessentials.presence.application.port.PresenceStore;
import com.uxplima.uxmessentials.presence.domain.PlayerPresence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published presence query: it answers from the same map the AFK sweep scans, it tells an offline player
 * apart from a present one, and asking about a stranger does not enrol them.
 */
class PresenceQueriesTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final Instant NOON = Instant.parse("2026-08-09T12:00:00Z");

    private FakePresenceStore store;

    @BeforeEach
    void setUp() {
        store = new FakePresenceStore();
    }

    @Test
    void aPlayerWhoIsHereCarriesTheirAfkFlagAndTheirLastActivity() {
        store.put(ALICE, PlayerPresence.active(NOON).markAfk(Optional.of("lunch")));

        UxmPresence presence = queries().of(ALICE.uuid()).orElseThrow();

        assertThat(presence.playerId()).isEqualTo(ALICE.uuid());
        assertThat(presence.afk()).isTrue();
        assertThat(presence.afkReason()).contains("lunch");
        assertThat(presence.lastActivity()).isEqualTo(NOON);
    }

    @Test
    void anAutomaticFlagCarriesNoReason() {
        store.put(ALICE, PlayerPresence.active(NOON).markAfk(Optional.empty()));

        assertThat(queries().of(ALICE.uuid()).orElseThrow().afkReason())
                .as("the idle sweep flags a player without one, and inventing one would be a lie")
                .isEmpty();
    }

    @Test
    void aPlayerWhoIsNotOnlineHasNoPresenceAtAll() {
        assertThat(queries().of(ALICE.uuid())).isEmpty();
        assertThat(queries().isAfk(ALICE.uuid()))
                .as("nobody is at an offline player's keyboard, but that is not what away means")
                .isFalse();
    }

    @Test
    void askingAboutAStrangerDoesNotEnrolThem() {
        queries().of(UUID.randomUUID());
        queries().isAfk(UUID.randomUUID());

        assertThat(store.seeded)
                .as("the store seeds a neutral state for a player it is asked about, so a query must not ask it")
                .isFalse();
    }

    @Test
    void theAwayListHoldsOnlyThePlayersWhoAreAway() {
        store.put(ALICE, PlayerPresence.active(NOON).markAfk(Optional.empty()));
        store.put(BOB, PlayerPresence.active(NOON));

        assertThat(queries().afk()).extracting(UxmPresence::playerId).containsExactly(ALICE.uuid());
    }

    @Test
    void nothingHereWaitsOnAnything() {
        store.put(ALICE, PlayerPresence.active(NOON));

        assertThat(queries().of(ALICE.uuid())).isPresent();
        assertThat(store.snapshots)
                .as("presence is a small in-memory map, so a copy of it is the whole cost of a read")
                .isEqualTo(1);
    }

    private PresenceQueries queries() {
        return new PresenceQueries(store);
    }

    /** Holds what it was given and shouts if a read takes the seeding path the live store would take. */
    private static final class FakePresenceStore implements PresenceStore {

        private final Map<PlayerRef, PlayerPresence> presences = new LinkedHashMap<>();
        private boolean seeded;
        private int snapshots;

        void put(PlayerRef who, PlayerPresence presence) {
            presences.put(who, presence);
        }

        @Override
        public PlayerPresence current(PlayerRef who) {
            seeded = true;
            return presences.getOrDefault(who, PlayerPresence.active(NOON));
        }

        @Override
        public PlayerPresence update(PlayerRef who, UnaryOperator<PlayerPresence> mutator) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public void forget(PlayerRef who) {
            throw new AssertionError("a query must never write");
        }

        @Override
        public Map<PlayerRef, PlayerPresence> snapshotAll() {
            snapshots++;
            return Map.copyOf(presences);
        }
    }
}
