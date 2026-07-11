package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqWarpMemberStore} against the default embedded SQLite backend with the Flyway
 * V1-V70 migrations applied. It proves a member round-trips with their role, that granting the same player a new
 * role upserts one row rather than inserting a second (the later role winning), that a remove revokes the
 * membership, and that the roster lists every member of the warp.
 */
class JooqWarpMemberStoreTest {

    private static final PlayerWarpId WARP = PlayerWarpId.of(1L);
    private static final Instant ADDED_AT = Instant.ofEpochMilli(1_000L);

    private Persistence persistence;
    private JooqWarpMemberStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqWarpMemberStore(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void puttingAMemberRoundTripsTheRole() {
        UUID player = UUID.randomUUID();

        store.put(WARP, new WarpMember(player, WarpRole.MANAGER, ADDED_AT));

        assertThat(store.roleOf(WARP, player)).contains(WarpRole.MANAGER);
        assertThat(store.list(WARP)).containsExactly(new WarpMember(player, WarpRole.MANAGER, ADDED_AT));
    }

    @Test
    void grantingANewRoleUpsertsTheSameRow() {
        UUID player = UUID.randomUUID();
        store.put(WARP, new WarpMember(player, WarpRole.OWNER, ADDED_AT));

        store.put(WARP, new WarpMember(player, WarpRole.CO_OWNER, ADDED_AT));

        assertThat(store.roleOf(WARP, player)).contains(WarpRole.CO_OWNER);
        assertThat(store.list(WARP)).hasSize(1);
    }

    @Test
    void removingAMemberRevokesTheMembership() {
        UUID player = UUID.randomUUID();
        store.put(WARP, new WarpMember(player, WarpRole.CO_OWNER, ADDED_AT));

        store.remove(WARP, player);

        assertThat(store.roleOf(WARP, player)).isEmpty();
        assertThat(store.list(WARP)).isEmpty();
    }

    @Test
    void listReturnsEveryMemberOfTheWarp() {
        WarpMember coOwner = new WarpMember(UUID.randomUUID(), WarpRole.CO_OWNER, ADDED_AT);
        WarpMember manager = new WarpMember(UUID.randomUUID(), WarpRole.MANAGER, ADDED_AT);
        store.put(WARP, coOwner);
        store.put(WARP, manager);

        assertThat(store.list(WARP)).containsExactlyInAnyOrder(coOwner, manager);
    }

    @Test
    void roleOfIsEmptyForANonMember() {
        assertThat(store.roleOf(WARP, UUID.randomUUID())).isEmpty();
    }

    /** A config that selects the embedded SQLite backend with every default — no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
