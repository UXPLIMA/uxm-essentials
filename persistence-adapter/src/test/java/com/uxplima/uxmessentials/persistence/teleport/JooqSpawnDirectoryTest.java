package com.uxplima.uxmessentials.persistence.teleport;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Main spawns per scope through the real migrations on a SQLite file, and the cache that reloads them. */
@EveryBackend
class JooqSpawnDirectoryTest {

    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final ServerScope LOBBY_1 = ServerScope.server("lobby-1");
    private static final ServerScope LOBBY_SET = ServerScope.set("lobby");

    private JooqSpawnDirectory store;

    @BeforeEach
    void open(TestDatabase database) {
        DSLContext dsl = database.dsl();
        store = new JooqSpawnDirectory(dsl);
    }

    private static Position at(double x) {
        return new Position(WORLD, x, 64, 0, 0, 0);
    }

    @Test
    void eachScopeKeepsItsOwnMainSpawnBesideTheNetworkOne() {
        store.setMainSpawn(null, at(1));
        store.setMainSpawn(LOBBY_1, at(2));
        store.setMainSpawn(LOBBY_SET, at(3));

        assertThat(store.mainSpawn(null)).contains(at(1));
        assertThat(store.mainSpawn(LOBBY_1)).contains(at(2));
        assertThat(store.mainSpawn(LOBBY_SET)).contains(at(3));
        assertThat(store.mainSpawn(ServerScope.server("survival"))).isEmpty();
    }

    @Test
    void settingAScopeAgainReplacesOnlyThatScope() {
        store.setMainSpawn(null, at(1));
        store.setMainSpawn(LOBBY_1, at(2));
        store.setMainSpawn(LOBBY_SET, at(3));

        store.setMainSpawn(LOBBY_1, at(9));
        store.setMainSpawn(null, at(8));

        assertThat(store.mainSpawn(LOBBY_1)).contains(at(9));
        assertThat(store.mainSpawn(LOBBY_SET)).contains(at(3));
        assertThat(store.mainSpawn(null)).contains(at(8));
    }

    @Test
    void theCacheLoadsEveryScopeAndTakesInAPeersScopedSpawnOnReload() {
        store.setMainSpawn(null, at(1));
        CachedSpawnDirectory cache = new CachedSpawnDirectory(store, () -> java.util.Optional.of(store.snapshot()));
        cache.warmAll();
        assertThat(cache.mainSpawn(LOBBY_1)).isEmpty();

        // A peer writes the shared rows directly; this server sees it on the next reload.
        store.setMainSpawn(LOBBY_1, at(2));
        cache.warmAll();

        assertThat(cache.mainSpawn(null)).contains(at(1));
        assertThat(cache.mainSpawn(LOBBY_1)).contains(at(2));
    }
}
