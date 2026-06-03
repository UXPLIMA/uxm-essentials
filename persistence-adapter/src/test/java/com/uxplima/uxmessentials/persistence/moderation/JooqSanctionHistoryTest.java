package com.uxplima.uxmessentials.persistence.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqSanctionHistory} against the default embedded SQLite backend with the
 * Flyway V11 history table applied. It proves a row round-trips its shape (action, issuer, reason, expiry,
 * ip), that {@code banHistory} returns only the ban-family rows and {@code muteHistory} only the mute-family
 * rows, both newest-first and capped, and that the table is append-only (a lift row coexists with the ban it
 * ended rather than overwriting it).
 */
class JooqSanctionHistoryTest {

    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");
    private static final Issuer STAFF = Issuer.console("admin");

    private Persistence persistence;
    private JooqSanctionHistory history;
    private UUID alice;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        history = new JooqSanctionHistory(persistence.dsl());
        alice = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void banRowRoundTripsItsShape() {
        Instant until = T0.plusSeconds(3600);
        history.append(new SanctionHistoryEntry(
                SanctionAction.BAN, alice, STAFF, Optional.of("griefing"), T0, Optional.of(until), Optional.empty()));

        List<SanctionHistoryEntry> rows = history.banHistory(alice, 20);
        assertThat(rows).hasSize(1);
        SanctionHistoryEntry row = rows.get(0);
        assertThat(row.action()).isEqualTo(SanctionAction.BAN);
        assertThat(row.reason()).contains("griefing");
        assertThat(row.expiry()).contains(until);
        assertThat(row.at()).isEqualTo(T0);
        assertThat(row.actor().name()).isEqualTo("admin");
    }

    @Test
    void ipBanRowRoundTripsItsAddress() {
        history.append(new SanctionHistoryEntry(
                SanctionAction.BAN,
                new UUID(0L, 0L),
                STAFF,
                Optional.empty(),
                T0,
                Optional.empty(),
                Optional.of("203.0.113.7")));

        SanctionHistoryEntry row = history.banHistory(new UUID(0L, 0L), 20).get(0);
        assertThat(row.ip()).contains("203.0.113.7");
        assertThat(row.expiry()).isEmpty();
    }

    @Test
    void banHistoryReturnsOnlyBanFamilyNewestFirstAndIsAppendOnly() {
        history.append(entry(SanctionAction.BAN, T0, Optional.of("first")));
        history.append(entry(SanctionAction.MUTE, T0.plusSeconds(30), Optional.of("spam")));
        history.append(entry(SanctionAction.UNBAN, T0.plusSeconds(60), Optional.empty()));

        List<SanctionHistoryEntry> bans = history.banHistory(alice, 20);
        // Both the ban and its later lift are present (append-only), newest-first; the mute is excluded.
        assertThat(bans)
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.UNBAN, SanctionAction.BAN);
    }

    @Test
    void muteHistoryReturnsOnlyMuteFamily() {
        history.append(entry(SanctionAction.MUTE, T0, Optional.of("spam")));
        history.append(entry(SanctionAction.UNMUTE, T0.plusSeconds(60), Optional.empty()));
        history.append(entry(SanctionAction.BAN, T0.plusSeconds(30), Optional.of("grief")));

        assertThat(history.muteHistory(alice, 20))
                .extracting(SanctionHistoryEntry::action)
                .containsExactly(SanctionAction.UNMUTE, SanctionAction.MUTE);
    }

    @Test
    void readIsCappedAtTheLimit() {
        history.append(entry(SanctionAction.BAN, T0, Optional.of("a")));
        history.append(entry(SanctionAction.UNBAN, T0.plusSeconds(10), Optional.empty()));
        history.append(entry(SanctionAction.BAN, T0.plusSeconds(20), Optional.of("b")));

        assertThat(history.banHistory(alice, 2)).hasSize(2);
    }

    private SanctionHistoryEntry entry(SanctionAction action, Instant at, Optional<String> reason) {
        return new SanctionHistoryEntry(action, alice, STAFF, reason, at, Optional.empty(), Optional.empty());
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
