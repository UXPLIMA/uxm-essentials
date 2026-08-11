package com.uxplima.uxmessentials.persistence.ip;

import static com.uxplima.uxmessentials.persistence.jooq.tables.IpHistory.IP_HISTORY;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.AltGroup;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqIpHistoryStore} against the embedded SQLite backend with the Flyway V83
 * {@code ip_history} table applied. It proves the one capture behind both {@code /alts} and {@code /ipalts}:
 * recorded associations group the accounts sharing a token, a repeat connection slides {@code last_seen} forward
 * instead of duplicating the row, the raw address is stored only when one is passed (and a later null does not
 * erase it), and the association column itself only ever holds the keyed token, never an address.
 */
class JooqIpHistoryStoreTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String RAW_IP = "203.0.113.7";

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
    void groupsAccountsSharingAnIpToken() {
        UUID target = UUID.randomUUID();
        UUID alt = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        String shared = token("10.0.0.1");
        store.record(target, shared, null, NOW);
        store.record(alt, shared, null, NOW);
        store.record(far, token("10.0.0.2"), null, NOW);

        AltGroup group = AltGroup.of(target, store.sharingTokenWith(target));

        assertThat(group.alts()).containsExactly(alt);
    }

    @Test
    void countsTheDistinctAccountsOnAToken() {
        String shared = token("10.0.0.1");
        UUID a = UUID.randomUUID();
        store.record(a, shared, null, NOW);
        store.record(UUID.randomUUID(), shared, null, NOW);
        // The same account twice must not inflate the distinct count the join-time cap reads.
        store.record(a, shared, null, NOW.plusSeconds(60));

        assertThat(store.accountsOnToken(shared)).hasSize(2);
    }

    @Test
    void aRepeatConnectionSlidesLastSeenForwardAndKeepsFirstSeen() {
        UUID account = UUID.randomUUID();
        String shared = token("10.0.0.1");
        store.record(account, shared, null, NOW);
        store.record(account, shared, null, NOW.plusSeconds(3_600));

        var row = persistence
                .dsl()
                .select(IP_HISTORY.FIRST_SEEN, IP_HISTORY.LAST_SEEN)
                .from(IP_HISTORY)
                .where(IP_HISTORY.UUID.eq(account.toString()))
                .fetchSingle();

        assertThat(row.value1()).isEqualTo(NOW.toEpochMilli());
        assertThat(row.value2()).isEqualTo(NOW.plusSeconds(3_600).toEpochMilli());
    }

    @Test
    void theRawAddressIsStoredOnlyWhenOneIsPassed() {
        UUID retained = UUID.randomUUID();
        UUID tokenOnly = UUID.randomUUID();
        store.record(retained, token(RAW_IP), RAW_IP, NOW);
        store.record(tokenOnly, token(RAW_IP), null, NOW);

        assertThat(store.addressesOf(retained)).containsExactly(RAW_IP);
        // Without moderation nothing passes an address, so the STRICT-ban lookup finds none to fan out to.
        assertThat(store.addressesOf(tokenOnly)).isEmpty();
    }

    @Test
    void aLaterTokenOnlyRecordDoesNotEraseARetainedAddress() {
        UUID account = UUID.randomUUID();
        store.record(account, token(RAW_IP), RAW_IP, NOW);
        // Moderation switched off between the two joins: new rows stop retaining, old ones stay as they are.
        store.record(account, token(RAW_IP), null, NOW.plusSeconds(60));

        assertThat(store.addressesOf(account)).containsExactly(RAW_IP);
    }

    @Test
    void theAssociationColumnOnlyEverHoldsTheOneWayToken() {
        store.record(UUID.randomUUID(), token(RAW_IP), RAW_IP, NOW);

        assertThat(persistence
                        .dsl()
                        .select(IP_HISTORY.IP_TOKEN)
                        .from(IP_HISTORY)
                        .fetch(IP_HISTORY.IP_TOKEN))
                .isNotEmpty()
                .noneMatch(value -> value.contains(RAW_IP));
    }

    /** The SHA-256 hex token the adapter derives from an address, replicated so the test asserts on a real token. */
    private static String token(String ip) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(ip.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK is missing SHA-256", e);
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
