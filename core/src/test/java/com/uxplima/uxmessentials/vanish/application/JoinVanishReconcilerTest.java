package com.uxplima.uxmessentials.vanish.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.junit.jupiter.api.Test;

/**
 * {@link JoinVanishReconciler} seeds a joining player's local vanish state from the network-wide view, so a player
 * vanished on another backend arrives already marked vanished here. A player the network does not report vanished is
 * left untouched, and with an {@link NetworkVanishStore#empty() empty} view (cross-server off) the reconcile is inert.
 */
class JoinVanishReconcilerTest {

    private final FakeStore store = new FakeStore();
    private final FakeNetwork network = new FakeNetwork();
    private final JoinVanishReconciler reconciler = new JoinVanishReconciler(network, store);

    private final UUID joiner = UUID.randomUUID();

    @Test
    void seedsTheLocalStoreWhenTheNetworkReportsTheJoinerVanished() {
        network.levels.put(joiner, VanishLevel.of(2));

        Optional<VanishLevel> seeded = reconciler.reconcile(joiner);

        assertThat(seeded).contains(VanishLevel.of(2));
        assertThat(store.isVanished(joiner)).isTrue();
        assertThat(store.levelOf(joiner)).contains(VanishLevel.of(2));
    }

    @Test
    void leavesAJoinerTheNetworkDoesNotReportUntouched() {
        Optional<VanishLevel> seeded = reconciler.reconcile(joiner);

        assertThat(seeded).isEmpty();
        assertThat(store.isVanished(joiner)).isFalse();
    }

    @Test
    void isInertWithTheEmptyNetworkView() {
        JoinVanishReconciler inert = new JoinVanishReconciler(NetworkVanishStore.empty(), store);

        assertThat(inert.reconcile(joiner)).isEmpty();
        assertThat(store.isVanished(joiner)).isFalse();
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
}
