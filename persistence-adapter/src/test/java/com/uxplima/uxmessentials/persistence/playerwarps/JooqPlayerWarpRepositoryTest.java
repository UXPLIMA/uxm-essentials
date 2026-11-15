package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The player warp queries the rent sweep deletes by, through the real migrations on a SQLite file. */
class JooqPlayerWarpRepositoryTest {

    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"), "world");
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Instant CUTOFF = NOW.minus(Duration.ofDays(30));

    @TempDir
    Path folder;

    private JooqPlayerWarpRepository repository;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        repository =
                new JooqPlayerWarpRepository(DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), ""));
    }

    private static PlayerWarp warp(String name) {
        return PlayerWarp.create(
                SAM, "Sam", PlayerWarpName.of(name), Position.of(WORLD, 0, 64, 0), NOW.minus(Duration.ofDays(90)));
    }

    private static PlayerWarp archivedForRent(String name, Instant deadline, Instant lastChange) {
        RentState unpaid = new RentState(
                deadline.minus(Duration.ofDays(3)),
                Optional.of(deadline.minus(Duration.ofDays(3))),
                Optional.of(deadline));
        return warp(name).withRent(unpaid, lastChange).withStatus(WarpStatus.ARCHIVED, lastChange);
    }

    @Test
    void onlyWarpsArchivedForRentAndUntouchedSinceTheCutoffAreDueForHardDelete() {
        Instant old = CUTOFF.minus(Duration.ofDays(1));
        repository.save(archivedForRent("late", CUTOFF, CUTOFF));
        repository.save(archivedForRent("older", old, old));
        repository.save(archivedForRent("recent", NOW.minus(Duration.ofDays(1)), NOW.minus(Duration.ofDays(1))));
        repository.save(archivedForRent("rearchived", old, NOW));
        repository.save(archivedForRent("restored", old, old).withStatus(WarpStatus.ACTIVE, old));
        repository.save(warp("owner-archived").withStatus(WarpStatus.ARCHIVED, old));

        List<String> due = repository.archivedForRent(CUTOFF, 10).stream()
                .map(w -> w.name().value())
                .toList();

        assertThat(due).containsExactly("older", "late");
        assertThat(repository.archivedForRent(CUTOFF, 1)).hasSize(1);
    }
}
