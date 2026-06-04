package com.uxplima.uxmessentials.moderation.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.JailDirectory;
import com.uxplima.uxmessentials.moderation.application.port.JailLocationStore;
import com.uxplima.uxmessentials.moderation.domain.StoredJail;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/**
 * {@link CombinedJailDirectory}: the union of the read-only config jails and the DB-backed jails. A jail
 * exists when either source knows it, {@code names} is the sorted distinct union (a name in both appears once),
 * and the countdown mode is read from the config so a stored-only jail defaults to the module's mode.
 */
class CombinedJailDirectoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    @Test
    void existsWhenEitherSourceKnowsTheName() {
        JailDirectory combined = new CombinedJailDirectory(config(Set.of("configjail"), Set.of()), store("storedjail"));

        assertThat(combined.exists("configjail")).isTrue();
        assertThat(combined.exists("storedjail")).isTrue();
        assertThat(combined.exists("missing")).isFalse();
    }

    @Test
    void existsFoldsTheLookupCaseForTheStore() {
        JailDirectory combined = new CombinedJailDirectory(config(Set.of(), Set.of()), store("storedjail"));

        assertThat(combined.exists("StoredJail")).isTrue();
    }

    @Test
    void namesAreTheSortedDistinctUnion() {
        JailDirectory combined =
                new CombinedJailDirectory(config(Set.of("zeta", "shared"), Set.of()), store("alpha", "shared"));

        assertThat(combined.names()).containsExactly("alpha", "shared", "zeta");
    }

    @Test
    void peekNamesReturnsOnlyConfigNamesWithoutTouchingTheStore() {
        JailDirectory combined = new CombinedJailDirectory(config(Set.of("zeta", "shared"), Set.of()), throwingStore());

        // peekNames must stay non-blocking for tab-completion: it reads the config seam only, never the
        // DB-backed store, so a store that throws on names() proves the store is not consulted here.
        assertThat(combined.peekNames()).containsExactly("shared", "zeta");
    }

    @Test
    void countdownModeComesFromTheConfig() {
        JailDirectory combined = new CombinedJailDirectory(config(Set.of("wall"), Set.of("wall")), store("storedjail"));

        assertThat(combined.isWallClock("wall")).isTrue();
        assertThat(combined.isWallClock("storedjail")).isFalse();
    }

    private static JailDirectory config(Set<String> names, Set<String> wallClock) {
        return new FixedConfig(names, wallClock);
    }

    private static JailLocationStore store(String... names) {
        FixedStore store = new FixedStore();
        for (String name : names) {
            store.save(new StoredJail(name, Position.of(WORLD, 0, 64, 0)));
        }
        return store;
    }

    /** A store whose name listing fails, standing in for the DB-backed source peekNames must not consult. */
    private static JailLocationStore throwingStore() {
        return new FixedStore() {
            @Override
            public List<String> names() {
                throw new AssertionError("peekNames must not read the DB-backed store");
            }
        };
    }

    private static final class FixedConfig implements JailDirectory {
        private final Set<String> configured;
        private final Set<String> wallClock;

        FixedConfig(Set<String> configured, Set<String> wallClock) {
            this.configured = configured;
            this.wallClock = wallClock;
        }

        @Override
        public boolean exists(String jail) {
            return configured.contains(jail);
        }

        @Override
        public boolean isWallClock(String jail) {
            return wallClock.contains(jail);
        }

        @Override
        public List<String> names() {
            return configured.stream().sorted().toList();
        }
    }

    private static class FixedStore implements JailLocationStore {
        private final Map<String, StoredJail> byName = new HashMap<>();

        @Override
        public void save(StoredJail jail) {
            byName.put(jail.name(), jail);
        }

        @Override
        public boolean exists(String name) {
            return byName.containsKey(name);
        }

        @Override
        public Optional<StoredJail> find(String name) {
            return Optional.ofNullable(byName.get(name));
        }

        @Override
        public boolean delete(String name) {
            return byName.remove(name) != null;
        }

        @Override
        public List<String> names() {
            return byName.keySet().stream().sorted().toList();
        }
    }
}
