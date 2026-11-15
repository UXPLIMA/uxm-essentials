package com.uxplima.uxmessentials.persistence.holograms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** A hologram's server scope through the real migrations on a SQLite file. */
class JooqHologramRepositoryTest {

    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final HologramName BOARD = HologramName.of("board");

    @TempDir
    Path folder;

    private JooqHologramRepository store;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        DSLContext dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        store = new JooqHologramRepository(dsl);
    }

    private static Hologram board() {
        return Hologram.create(
                BOARD,
                new Position(WORLD, 0, 70, 0, 0, 0),
                List.of(new HologramLine("Welcome")),
                Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void anUnscopedHologramReadsBackForEveryServer() {
        store.save(board());

        assertThat(store.find(BOARD).orElseThrow().scope()).isNull();
    }

    @Test
    void aScopeIsStoredAndReplacedAndCleared() {
        store.save(board().withScope(ServerScope.set("lobby")));
        assertThat(store.find(BOARD).orElseThrow().scope()).isEqualTo(ServerScope.set("lobby"));

        store.save(store.find(BOARD).orElseThrow().withScope(ServerScope.server("survival")));
        assertThat(store.find(BOARD).orElseThrow().scope()).isEqualTo(ServerScope.server("survival"));

        store.save(store.find(BOARD).orElseThrow().withScope(null));
        assertThat(store.all()).singleElement().extracting(Hologram::scope).isNull();
    }
}
