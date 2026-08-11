package com.uxplima.uxmessentials.persistence.ip;

import static com.uxplima.uxmessentials.persistence.jooq.tables.IpHistory.IP_HISTORY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationIpHistory.MODERATION_IP_HISTORY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationSeen.MODERATION_SEEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.persistence.runtime.Transactions;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.IpTokens;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one-shot move of the moderation context's raw address history into the consolidated {@code ip_history}
 * table. It proves the alt history staff already had survives the upgrade (both the history rows and the
 * pre-history last-seen address), that the raw copy is deleted as it goes, that retention follows the moderation
 * module rather than the move itself, and that a second run is a no-op.
 */
class LegacyIpHistoryBackfillTest {

    private static final Instant T0 = Instant.ofEpochSecond(1_700_000_000L);

    private Persistence persistence;
    private IpHistoryStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = IpHistoryStores.jooq(persistence);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void foldsLegacyHistoryRowsInAndDeletesTheRawCopy() {
        UUID target = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        legacyHistory(target, "198.51.100.5");
        legacyHistory(alt, "198.51.100.5");

        int moved = LegacyIpHistoryBackfill.run(persistence, new PrefixTokens(), true);

        assertThat(moved).isEqualTo(2);
        // The shared address became a shared token, so /alts still surfaces the alt after the move.
        assertThat(store.accountsOnToken("token:198.51.100.5")).containsExactlyInAnyOrder(target, alt);
        // With moderation enabled the address comes across too, so a STRICT ban still has one to fan out to.
        assertThat(store.addressesOf(target)).containsExactly("198.51.100.5");
        assertThat(persistence.dsl().fetchCount(MODERATION_IP_HISTORY)).isZero();
    }

    @Test
    void carriesTheLastSeenAddressOfAPlayerWithNoHistoryRows() {
        UUID account = UUID.randomUUID();
        legacySeen(account, "10.0.0.9");

        LegacyIpHistoryBackfill.run(persistence, new PrefixTokens(), true);

        assertThat(store.accountsOnToken("token:10.0.0.9")).containsExactly(account);
        // The seen row itself stays: it is what /seen and /seenip render.
        assertThat(persistence.dsl().fetchCount(MODERATION_SEEN)).isEqualTo(1);
    }

    @Test
    void keepsOnlyTheTokenWhenModerationIsDisabled() {
        UUID account = UUID.randomUUID();
        legacyHistory(account, "203.0.113.7");

        LegacyIpHistoryBackfill.run(persistence, new PrefixTokens(), false);

        assertThat(store.accountsOnToken("token:203.0.113.7")).containsExactly(account);
        assertThat(store.addressesOf(account)).isEmpty();
        // The raw copy is still gone: a server that no longer retains addresses keeps none of the old ones either.
        assertThat(persistence.dsl().fetchCount(MODERATION_IP_HISTORY)).isZero();
    }

    @Test
    void aSecondRunMovesNothing() {
        UUID account = UUID.randomUUID();
        legacyHistory(account, "198.51.100.5");
        legacySeen(account, "198.51.100.5");
        LegacyIpHistoryBackfill.run(persistence, new PrefixTokens(), true);

        assertThat(LegacyIpHistoryBackfill.run(persistence, new PrefixTokens(), true))
                .isZero();
        assertThat(persistence.dsl().fetchCount(IP_HISTORY)).isEqualTo(1);
    }

    /** A row the pre-V83 moderation capture would have written on join. Seeded in its own committed transaction:
     * the pool hands out connections with auto-commit off, so an uncommitted insert is invisible to the next read. */
    private void legacyHistory(UUID account, String address) {
        Transactions.inTransaction(
                persistence.dsl(),
                dsl -> dsl.insertInto(MODERATION_IP_HISTORY)
                        .set(MODERATION_IP_HISTORY.UUID, account.toString())
                        .set(MODERATION_IP_HISTORY.IP, address)
                        .set(MODERATION_IP_HISTORY.FIRST_SEEN, T0.toEpochMilli())
                        .set(MODERATION_IP_HISTORY.LAST_SEEN, T0.plusSeconds(60).toEpochMilli())
                        .execute());
    }

    /** The last-seen row, the only address a player who predates the history table has. */
    private void legacySeen(UUID account, String address) {
        Transactions.inTransaction(
                persistence.dsl(),
                dsl -> dsl.insertInto(MODERATION_SEEN)
                        .set(MODERATION_SEEN.UUID, account.toString())
                        .set(MODERATION_SEEN.NAME, "player")
                        .set(MODERATION_SEEN.LAST_IP, address)
                        .set(MODERATION_SEEN.FIRST_SEEN, T0.toEpochMilli())
                        .set(MODERATION_SEEN.LAST_SEEN, T0.toEpochMilli())
                        .execute());
    }

    /** A reversible stand-in for the keyed hash, so the assertions can name the token an address maps to. */
    private record PrefixTokens() implements IpTokens {
        @Override
        public String tokenFor(String address) {
            return "token:" + address;
        }
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
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
