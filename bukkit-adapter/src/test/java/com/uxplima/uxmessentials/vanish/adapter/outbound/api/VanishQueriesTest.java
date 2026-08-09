package com.uxplima.uxmessentials.vanish.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published vanish query: it reads the one vanish authority, and it answers "can this viewer see them" by the
 * level rule rather than by the flag, which is the difference that matters on a server with staff tiers.
 */
class VanishQueriesTest {

    private static final PlayerRef STAFF = new PlayerRef(UUID.randomUUID(), "Staff");
    private static final PlayerRef ADMIN = new PlayerRef(UUID.randomUUID(), "Admin");
    private static final PlayerRef PLAYER = new PlayerRef(UUID.randomUUID(), "Player");

    private FakeVanishStore store;
    private LevelTable levels;

    @BeforeEach
    void setUp() {
        store = new FakeVanishStore();
        levels = new LevelTable();
    }

    @Test
    void aHiddenPlayerIsReportedHiddenAtTheirLevel() {
        store.vanish(STAFF.uuid(), VanishLevel.of(2));

        assertThat(queries().isVanished(STAFF.uuid())).isTrue();
        assertThat(queries().levelOf(STAFF.uuid())).isEqualTo(2);
        assertThat(queries().vanished()).containsExactly(STAFF.uuid());
    }

    @Test
    void aPlayerWhoIsNotHiddenHasNoLevel() {
        assertThat(queries().isVanished(PLAYER.uuid())).isFalse();
        assertThat(queries().levelOf(PLAYER.uuid()))
                .as("zero is below every level a hidden player can hold, so it reads as not hidden")
                .isZero();
    }

    @Test
    void everybodySeesAPlayerWhoIsNotHidden() {
        assertThat(queries().canSee(PLAYER.uuid(), STAFF.uuid())).isTrue();
    }

    @Test
    void aPlayerAlwaysSeesThemselves() {
        store.vanish(STAFF.uuid(), VanishLevel.DEFAULT);

        assertThat(queries().canSee(STAFF.uuid(), STAFF.uuid())).isTrue();
    }

    @Test
    void aViewerWithNoSeeLevelDoesNotSeeAHiddenPlayer() {
        store.vanish(STAFF.uuid(), VanishLevel.DEFAULT);

        assertThat(queries().canSee(PLAYER.uuid(), STAFF.uuid())).isFalse();
    }

    @Test
    void aViewerWhoseLevelReachesTheirsSeesThem() {
        store.vanish(STAFF.uuid(), VanishLevel.of(2));
        levels.see(ADMIN, 1);

        assertThat(queries().canSee(ADMIN.uuid(), STAFF.uuid()))
                .as("a level below theirs is not enough, which is the whole point of the tiers")
                .isFalse();

        levels.see(ADMIN, 2);
        assertThat(queries().canSee(ADMIN.uuid(), STAFF.uuid())).isTrue();
    }

    @Test
    void theHiddenSetIsACopyRatherThanTheLiveOne() {
        store.vanish(STAFF.uuid(), VanishLevel.DEFAULT);
        Set<UUID> hidden = queries().vanished();

        store.reveal(STAFF.uuid());

        assertThat(hidden)
                .as("a consumer iterating the answer must not have it change underneath them")
                .containsExactly(STAFF.uuid());
    }

    private VanishQueries queries() {
        return new VanishQueries(
                store,
                levels,
                new QueryDoubles.MapLookup().with(STAFF).with(ADMIN).with(PLAYER));
    }

    /** The vanish state as a plain map, with the writes a query must never reach for left in place. */
    private static final class FakeVanishStore implements VanishStore {

        private final Map<UUID, VanishLevel> hidden = new HashMap<>();

        @Override
        public boolean isVanished(UUID who) {
            return hidden.containsKey(who);
        }

        @Override
        public void vanish(UUID who, VanishLevel level) {
            hidden.put(who, level);
        }

        @Override
        public void reveal(UUID who) {
            hidden.remove(who);
        }

        @Override
        public Optional<VanishLevel> levelOf(UUID who) {
            return Optional.ofNullable(hidden.get(who));
        }

        @Override
        public Set<UUID> vanished() {
            return Set.copyOf(hidden.keySet());
        }

        @Override
        public VanishState snapshot() {
            return new VanishState(hidden);
        }
    }

    /** Resolves the see level a test granted, and level one for the use level nothing here reads. */
    private static final class LevelTable implements VanishLevelResolver {

        private final Map<UUID, Integer> see = new HashMap<>();

        void see(PlayerRef who, int level) {
            see.put(who.uuid(), level);
        }

        @Override
        public VanishLevel useLevel(PlayerRef who) {
            return VanishLevel.DEFAULT;
        }

        @Override
        public int seeLevel(PlayerRef who) {
            return see.getOrDefault(who.uuid(), 0);
        }
    }
}
