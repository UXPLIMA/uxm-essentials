package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * A history row is written whole even when its reason is longer than the column: the sanction it records is already
 * stored, so the reason is fitted to the column's width rather than failing the write on a backend that enforces it.
 */
class JooqSanctionHistoryTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID ROBIN = UUID.fromString("00000000-0000-0000-0000-0000000000a3");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @TempDir
    Path folder;

    @Test
    void aReasonLongerThanTheColumnIsStoredFittedToIt() {
        SanctionHistory history = openStore();
        String reason = "reached 3 warnings, the last for: " + "x".repeat(240);

        history.append(new SanctionHistoryEntry(
                SanctionAction.MUTE,
                SAM,
                Issuer.of(ALEX),
                Optional.of(reason),
                NOW,
                Optional.empty(),
                Optional.empty()));

        List<SanctionHistoryEntry> stored = history.muteHistory(SAM, 10);
        assertThat(stored)
                .singleElement()
                .satisfies(entry -> assertThat(entry.reason()).hasValueSatisfying(kept -> {
                    assertThat(kept).hasSizeLessThanOrEqualTo(256);
                    assertThat(reason).startsWith(kept);
                }));
    }

    @Test
    void aBatchIsStoredWholeWithKeysAfterTheRowsAlreadyThere() {
        SanctionHistory history = openStore();
        history.append(row(SanctionAction.WARN, SAM, Optional.empty(), NOW));

        history.appendAll(List.of(
                row(SanctionAction.KICK, SAM, Optional.empty(), NOW.plusSeconds(1)),
                row(SanctionAction.KICK, ROBIN, Optional.empty(), NOW.plusSeconds(1))));

        assertThat(history.recentForTarget(SAM, 10))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.KICK, SanctionAction.WARN);
        assertThat(history.recentForTarget(ROBIN, 10)).hasSize(1);
        assertThat(history.recentByActor(ALEX.uuid(), 10)).hasSize(3);
    }

    @Test
    void theRevocableReadSkipsKicksLiftsAndIpRowsBeforeTheLimit() {
        SanctionHistory history = openStore();
        history.append(row(SanctionAction.BAN, SAM, Optional.empty(), NOW));
        history.append(row(SanctionAction.MUTE, ROBIN, Optional.empty(), NOW.plusSeconds(1)));
        history.append(row(SanctionAction.BAN, SAM, Optional.of("198.51.100.9"), NOW.plusSeconds(2)));
        history.append(row(SanctionAction.UNMUTE, ROBIN, Optional.empty(), NOW.plusSeconds(3)));
        history.appendAll(List.of(
                row(SanctionAction.KICK, SAM, Optional.empty(), NOW.plusSeconds(4)),
                row(SanctionAction.KICK, ROBIN, Optional.empty(), NOW.plusSeconds(4))));

        assertThat(history.revocableByActor(ALEX.uuid(), 2))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.MUTE, SanctionAction.BAN);
    }

    private static SanctionHistoryEntry row(SanctionAction action, UUID target, Optional<String> ip, Instant at) {
        return new SanctionHistoryEntry(action, target, Issuer.of(ALEX), Optional.empty(), at, Optional.empty(), ip);
    }

    private SanctionHistory openStore() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        DSLContext dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        return new JooqSanctionHistory(dsl);
    }
}
