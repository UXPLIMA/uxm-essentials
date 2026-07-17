package com.uxplima.uxmessentials.vanish.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.junit.jupiter.api.Test;

/**
 * {@link ListVanished} filters the vanished set by the caller's see level: it lists the players the caller could
 * already see and omits those hidden above their level, so the list can never leak a higher-level vanished admin
 * to a lower-level caller. Phase 5 makes the roster network-wide by folding in the {@link NetworkVanishStore}, still
 * scoped to the caller's see level.
 */
class ListVanishedTest {

    private final FakeStore store = new FakeStore();
    private final FakeLevels levels = new FakeLevels();

    private final PlayerRef caller = new PlayerRef(UUID.randomUUID(), "Caller");
    private final UUID lowVanished = UUID.randomUUID();
    private final UUID highVanished = UUID.randomUUID();

    private ListVanished local() {
        return new ListVanished(store, levels, NetworkVanishStore.empty());
    }

    @Test
    void anEmptyStoreListsNobody() {
        assertThat(local().visibleTo(caller)).isEmpty();
    }

    @Test
    void aSeeLevelOneCallerListsOnlyThePlayersTheyCanSee() {
        store.vanish(lowVanished, VanishLevel.DEFAULT); // use level 1
        store.vanish(highVanished, VanishLevel.of(3)); // use level 3
        levels.seeLevels.put(caller.uuid(), 1);

        assertThat(local().visibleTo(caller)).containsExactly(lowVanished);
    }

    @Test
    void aHighSeeLevelCallerListsEveryone() {
        store.vanish(lowVanished, VanishLevel.DEFAULT);
        store.vanish(highVanished, VanishLevel.of(3));
        levels.seeLevels.put(caller.uuid(), 5);

        assertThat(local().visibleTo(caller)).containsExactlyInAnyOrder(lowVanished, highVanished);
    }

    @Test
    void aggregatesNetworkVanishedPlayersScopedToTheSeeLevel() {
        UUID remoteLow = UUID.randomUUID();
        UUID remoteHigh = UUID.randomUUID();
        FakeNetwork network = new FakeNetwork();
        network.levels.put(remoteLow, VanishLevel.DEFAULT); // hidden on another backend at level 1
        network.levels.put(remoteHigh, VanishLevel.of(4)); // hidden on another backend at level 4
        store.vanish(lowVanished, VanishLevel.DEFAULT); // hidden locally
        levels.seeLevels.put(caller.uuid(), 1);

        assertThat(new ListVanished(store, levels, network).visibleTo(caller))
                .containsExactlyInAnyOrder(lowVanished, remoteLow); // remoteHigh (level 4) hidden above see level 1
    }

    private static final class FakeNetwork implements NetworkVanishStore {
        private final Map<UUID, VanishLevel> levels = new HashMap<>();

        @Override
        public void apply(VanishSync change) {}

        @Override
        public Optional<VanishLevel> levelOf(UUID who) {
            return Optional.ofNullable(levels.get(who));
        }

        @Override
        public Optional<String> nameOf(UUID who) {
            return Optional.empty();
        }

        @Override
        public Map<UUID, VanishLevel> levels() {
            return Map.copyOf(levels);
        }

        @Override
        public void clear() {
            levels.clear();
        }
    }

    private static final class FakeStore implements VanishStore {
        private final ConcurrentHashMap<UUID, VanishLevel> vanished = new ConcurrentHashMap<>();

        @Override
        public boolean isVanished(UUID who) {
            return vanished.containsKey(who);
        }

        @Override
        public void vanish(UUID who, VanishLevel level) {
            vanished.put(who, level);
        }

        @Override
        public void reveal(UUID who) {
            vanished.remove(who);
        }

        @Override
        public Optional<VanishLevel> levelOf(UUID who) {
            return Optional.ofNullable(vanished.get(who));
        }

        @Override
        public Set<UUID> vanished() {
            return Set.copyOf(vanished.keySet());
        }

        @Override
        public VanishState snapshot() {
            return new VanishState(vanished);
        }
    }

    private static final class FakeLevels implements VanishLevelResolver {
        private final Map<UUID, Integer> seeLevels = new HashMap<>();

        @Override
        public VanishLevel useLevel(PlayerRef who) {
            return VanishLevel.DEFAULT;
        }

        @Override
        public int seeLevel(PlayerRef who) {
            return seeLevels.getOrDefault(who.uuid(), 0);
        }
    }
}
