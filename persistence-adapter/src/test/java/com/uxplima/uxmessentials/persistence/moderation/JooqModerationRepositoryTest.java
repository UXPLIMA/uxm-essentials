package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * A conditional ban write through the real migrations on a SQLite file: it lands only while the row is still the one
 * the caller read, so a ban written by another server in between is never overwritten by a decision made without it.
 */
class JooqModerationRepositoryTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Sam");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @TempDir
    Path folder;

    private ModerationRepository first;
    private ModerationRepository second;

    @BeforeEach
    void open() {
        first = openStore();
        second = openStore();
        first.ensureUserExists(SAM, NOW);
    }

    private static TempbanState.Active ban(Duration span, String reason, Instant at) {
        return (TempbanState.Active) TempbanState.active(at.plus(span), Issuer.of(ALEX), Optional.of(reason), at);
    }

    @Test
    void aBanForAPlayerWithNoRowIsStored() {
        TempbanState.Active day = ban(Duration.ofDays(1), "grief", NOW);

        assertThat(first.replaceTempban(SAM, TempbanState.none(), day)).isTrue();

        assertThat(second.loadTempban(SAM)).isEqualTo(day);
    }

    @Test
    void aBanDecidedOnNoRowDoesNotLandOverOneWrittenSince() {
        TempbanState.Active permanent = ban(Duration.ofDays(365_000L), "cheating", NOW);
        second.saveTempban(SAM, permanent);

        assertThat(first.replaceTempban(SAM, TempbanState.none(), ban(Duration.ofDays(1), "spam", NOW)))
                .isFalse();

        assertThat(first.loadTempban(SAM)).isEqualTo(permanent);
    }

    @Test
    void aBanDecidedOnTheRowStillThereReplacesIt() {
        TempbanState.Active hour = ban(Duration.ofHours(1), "spam", NOW);
        first.saveTempban(SAM, hour);
        TempbanState.Active read = (TempbanState.Active) first.loadTempban(SAM);
        TempbanState.Active day = ban(Duration.ofDays(1), "spam again", NOW.plusSeconds(60));

        assertThat(first.replaceTempban(SAM, read, day)).isTrue();

        assertThat(second.loadTempban(SAM)).isEqualTo(day);
    }

    @Test
    void aBanDecidedOnARowThatChangedSinceDoesNotLand() {
        first.saveTempban(SAM, ban(Duration.ofHours(1), "spam", NOW));
        TempbanState.Active read = (TempbanState.Active) first.loadTempban(SAM);
        TempbanState.Active permanent = ban(Duration.ofDays(365_000L), "cheating", NOW.plusSeconds(1));
        second.saveTempban(SAM, permanent);

        assertThat(first.replaceTempban(SAM, read, ban(Duration.ofDays(1), "spam again", NOW.plusSeconds(2))))
                .isFalse();

        assertThat(first.loadTempban(SAM)).isEqualTo(permanent);
    }

    private ModerationRepository openStore() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        DSLContext dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        return new JooqModerationRepository(dsl);
    }
}
