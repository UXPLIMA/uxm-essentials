package com.uxplima.uxmessentials.persistence.npc;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
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

/** An NPC's server scope through the real migrations on a SQLite file. */
class JooqNpcRepositoryTest {

    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final NpcName GUIDE = NpcName.of("guide");

    @TempDir
    Path folder;

    private JooqNpcRepository store;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        DSLContext dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        store = new JooqNpcRepository(dsl);
    }

    private static Npc guide() {
        return Npc.create(GUIDE, new Position(WORLD, 0, 64, 0, 0, 0), null, Instant.parse("2030-01-01T00:00:00Z"));
    }

    @Test
    void anUnscopedNpcReadsBackForEveryServer() {
        store.save(guide());

        assertThat(store.find(GUIDE).orElseThrow().scope()).isNull();
    }

    @Test
    void aScopeIsStoredAndReplacedAndCleared() {
        store.save(guide().withScope(ServerScope.server("lobby-1")));
        assertThat(store.find(GUIDE).orElseThrow().scope()).isEqualTo(ServerScope.server("lobby-1"));

        store.save(store.find(GUIDE).orElseThrow().withScope(ServerScope.set("lobby")));
        assertThat(store.find(GUIDE).orElseThrow().scope()).isEqualTo(ServerScope.set("lobby"));

        store.save(store.find(GUIDE).orElseThrow().withScope(null));
        assertThat(store.all()).singleElement().extracting(Npc::scope).isNull();
    }
}
