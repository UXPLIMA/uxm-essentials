package com.uxplima.uxmessentials.playerstate.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.uxplima.uxmessentials.api.view.UxmGameMode;
import com.uxplima.uxmessentials.api.view.UxmPlayerState;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.playerstate.domain.SpeedValue;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published player-state query: it reports the switches the toggles wrote, it answers about online players
 * only, and asking about anybody else leaves the store exactly as it was.
 */
class PlayerStateQueriesTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final UUID STRANGER = UUID.randomUUID();

    private FakeStore store;
    private QueryDoubles.MapLookup players;

    @BeforeEach
    void setUp() {
        store = new FakeStore();
        players = new QueryDoubles.MapLookup().with(ALICE);
    }

    @Test
    void everySwitchComesBackAsTheSnapshotHoldsIt() {
        store.put(
                ALICE,
                PlayerStateSnapshot.initial()
                        .withGod(true)
                        .withFly(true)
                        .withGameMode(GameModeRef.CREATIVE)
                        .withWalkSpeed(SpeedValue.of(10.0)));

        UxmPlayerState state = queries().of(ALICE.uuid()).orElseThrow();

        assertThat(state.playerId()).isEqualTo(ALICE.uuid());
        assertThat(state.godMode()).isTrue();
        assertThat(state.flying()).isTrue();
        assertThat(state.gameMode()).contains(UxmGameMode.CREATIVE);
        assertThat(state.walkSpeed())
                .as("the speeds are published on Bukkit's scale, which is what a consumer would set")
                .isEqualTo(0.99f);
        assertThat(state.flySpeed()).isEqualTo(0.1f);
    }

    @Test
    void aPlayerWhoHasChangedNothingReadsAsTheNeutralState() {
        store.put(ALICE, PlayerStateSnapshot.initial());

        UxmPlayerState state = queries().of(ALICE.uuid()).orElseThrow();

        assertThat(state.godMode()).isFalse();
        assertThat(state.flying()).isFalse();
        assertThat(state.gameMode())
                .as("most players have no pinned mode, and empty says so rather than guessing survival")
                .isEmpty();
    }

    @Test
    void anOfflinePlayerIsEmptyRatherThanTheNeutralState() {
        assertThat(queries().of(STRANGER))
                .as("the state is dropped on quit, so there is nothing to report and nothing worth inventing")
                .isEmpty();
    }

    @Test
    void askingAboutAStrangerDoesNotEnrolThem() {
        queries().of(STRANGER);

        assertThat(store.snapshots).isEmpty();
    }

    private PlayerStateQueries queries() {
        return new PlayerStateQueries(store, players);
    }

    /** The real store seeds anybody it is asked about, and this one does the same so the guard is exercised. */
    private static final class FakeStore implements PlayerStateStore {

        private final ConcurrentHashMap<UUID, PlayerStateSnapshot> snapshots = new ConcurrentHashMap<>();

        void put(PlayerRef who, PlayerStateSnapshot snapshot) {
            snapshots.put(who.uuid(), snapshot);
        }

        @Override
        public PlayerStateSnapshot current(PlayerRef who) {
            return snapshots.computeIfAbsent(who.uuid(), ignored -> PlayerStateSnapshot.initial());
        }

        @Override
        public PlayerStateSnapshot update(PlayerRef who, UnaryOperator<PlayerStateSnapshot> mutator) {
            throw new AssertionError("a query must never change the state it reports");
        }

        @Override
        public void forget(PlayerRef who) {
            throw new AssertionError("a query must never change the state it reports");
        }
    }
}
