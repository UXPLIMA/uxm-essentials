package com.uxplima.uxmessentials.shared.adapter.outbound.playerdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.PlayerDataRepository;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore.NumericOp;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure-JUnit coverage of {@link CachingPlayerDataStore}'s logic over an in-memory fake repository and a synchronous
 * scheduler (so the async persist runs inline and the fake repository reflects every write). It pins the
 * {@link NumericOp} arithmetic for all five operations, the documented division-by-zero no-change, the
 * parse-or-fallback numeric view, the set/get/remove/all round-trips, and that a write reaches the repository while
 * a read stays a cache hit (and survives an evict-then-load round trip from the repository).
 */
class CachingPlayerDataStoreTest {

    private FakePlayerDataRepository repository;
    private CachingPlayerDataStore store;
    private UUID player;

    @BeforeEach
    void setUp() {
        repository = new FakePlayerDataRepository();
        store = new CachingPlayerDataStore(repository, new SyncScheduler());
        player = UUID.randomUUID();
    }

    @Test
    void setThenGetRoundTripsAndPersists() {
        store.set(player, "rank", "gold");

        assertThat(store.get(player, "rank")).contains("gold");
        assertThat(repository.value(player, "rank")).isEqualTo("gold");
    }

    @Test
    void getOfAnUnknownKeyIsEmpty() {
        assertThat(store.get(player, "missing")).isEmpty();
    }

    @Test
    void numberParsesTheStoredValueOrFallsBack() {
        store.set(player, "coins", "42");
        store.set(player, "name", "Alice");

        assertThat(store.number(player, "coins", -1.0)).isEqualTo(42.0);
        assertThat(store.number(player, "name", -1.0)).isEqualTo(-1.0); // not a number
        assertThat(store.number(player, "absent", 7.5)).isEqualTo(7.5); // missing
    }

    @Test
    void applySetIgnoresTheCurrentValue() {
        store.set(player, "k", "100");
        assertThat(store.apply(player, "k", NumericOp.SET, 5.0)).isEqualTo(5.0);
        assertThat(store.get(player, "k")).contains("5");
    }

    @Test
    void applyAddSubMulOnAMissingKeyTreatItAsZero() {
        assertThat(store.apply(player, "a", NumericOp.ADD, 3.0)).isEqualTo(3.0);
        assertThat(store.apply(player, "s", NumericOp.SUB, 4.0)).isEqualTo(-4.0);
        assertThat(store.apply(player, "m", NumericOp.MUL, 9.0)).isEqualTo(0.0);
    }

    @Test
    void applyAccumulatesAcrossCallsAndPersistsTheNewValue() {
        store.apply(player, "score", NumericOp.ADD, 10.0);
        double after = store.apply(player, "score", NumericOp.ADD, 5.0);

        assertThat(after).isEqualTo(15.0);
        assertThat(store.get(player, "score")).contains("15");
        assertThat(repository.value(player, "score")).isEqualTo("15");
    }

    @Test
    void applyMulAndDivCompute() {
        store.set(player, "v", "6");
        assertThat(store.apply(player, "v", NumericOp.MUL, 4.0)).isEqualTo(24.0);
        assertThat(store.apply(player, "v", NumericOp.DIV, 2.0)).isEqualTo(12.0);
        assertThat(store.get(player, "v")).contains("12");
    }

    @Test
    void applyDivByZeroLeavesTheValueAndReturnsTheCurrent() {
        store.set(player, "v", "20");

        double result = store.apply(player, "v", NumericOp.DIV, 0.0);

        assertThat(result).isEqualTo(20.0); // current value, no change, no throw
        assertThat(store.get(player, "v")).contains("20");
    }

    @Test
    void removeClearsTheKeyAndDeletesFromTheRepository() {
        store.set(player, "k", "v");

        store.remove(player, "k");

        assertThat(store.get(player, "k")).isEmpty();
        assertThat(repository.value(player, "k")).isNull();
    }

    @Test
    void allReturnsAnImmutableSnapshotOfEveryKey() {
        store.set(player, "a", "1");
        store.set(player, "b", "2");

        Map<String, String> all = store.all(player);

        assertThat(all)
                .containsOnly(
                        org.assertj.core.api.Assertions.entry("a", "1"),
                        org.assertj.core.api.Assertions.entry("b", "2"));
        assertThat(store.all(UUID.randomUUID())).isEmpty();
    }

    @Test
    void loadWarmsTheCacheFromTheRepositoryAndEvictDropsIt() {
        repository.upsert(player, "seed", "fromdb");

        // Before load the cache is cold, so the read misses.
        assertThat(store.get(player, "seed")).isEmpty();

        store.load(player);
        assertThat(store.get(player, "seed")).contains("fromdb");

        store.evict(player);
        assertThat(store.get(player, "seed")).isEmpty();
    }

    /** An in-memory {@link PlayerDataRepository} so the store's logic is exercised without a database. */
    private static final class FakePlayerDataRepository implements PlayerDataRepository {
        private final Map<UUID, Map<String, String>> rows = new ConcurrentHashMap<>();

        @Override
        public Map<String, String> loadAll(UUID player) {
            return new LinkedHashMap<>(rows.getOrDefault(player, Map.of()));
        }

        @Override
        public void upsert(UUID player, String key, String value) {
            rows.computeIfAbsent(player, p -> new ConcurrentHashMap<>()).put(key, value);
        }

        @Override
        public void delete(UUID player, String key) {
            Map<String, String> map = rows.get(player);
            if (map != null) {
                map.remove(key);
            }
        }

        @Nullable String value(UUID player, String key) {
            return rows.getOrDefault(player, new HashMap<>()).get(key);
        }
    }

    /** A scheduler that runs every task inline, so the async persist completes before the assertion. */
    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
