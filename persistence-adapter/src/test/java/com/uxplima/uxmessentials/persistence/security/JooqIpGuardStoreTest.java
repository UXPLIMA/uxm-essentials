package com.uxplima.uxmessentials.persistence.security;

import static com.uxplima.uxmessentials.persistence.jooq.tables.SecurityIp.SECURITY_IP;
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
import com.uxplima.uxmessentials.security.application.port.IpGuardStore;
import com.uxplima.uxmessentials.security.domain.AltGroup;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqIpGuardStore} against the embedded SQLite backend with the Flyway V78
 * {@code security_ip} table applied: recorded associations group accounts sharing an IP token, the per-IP account
 * count is distinct, re-recording the same link slides the last-seen forward, and — the security invariant — the
 * stored {@code ip_token} column holds a one-way token, never a raw address.
 */
class JooqIpGuardStoreTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String RAW_IP = "203.0.113.7";

    private Persistence persistence;
    private IpGuardStore store;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        store = new JooqIpGuardStore(persistence.dsl());
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
        store.record(target, shared, NOW);
        store.record(alt, shared, NOW);
        store.record(far, token("10.0.0.2"), NOW);

        AltGroup group = AltGroup.of(target, store.sharingIpWith(target));

        assertThat(group.alts()).containsExactly(alt);
    }

    @Test
    void countsTheDistinctAccountsOnAnIp() {
        String shared = token("10.0.0.1");
        UUID a = UUID.randomUUID();
        store.record(a, shared, NOW);
        store.record(UUID.randomUUID(), shared, NOW);
        // The same account twice must not inflate the distinct count.
        store.record(a, shared, NOW.plusSeconds(60));

        assertThat(store.accountsOnIp(shared)).hasSize(2);
    }

    @Test
    void storesAOneWayTokenAndNeverTheRawIp() {
        UUID account = UUID.randomUUID();
        String hashed = token(RAW_IP);
        store.record(account, hashed, NOW);

        String stored = persistence
                .dsl()
                .select(SECURITY_IP.IP_TOKEN)
                .from(SECURITY_IP)
                .where(SECURITY_IP.UUID.eq(account.toString()))
                .fetchOne(SECURITY_IP.IP_TOKEN);

        assertThat(stored).isEqualTo(hashed);
        // The plaintext address is never written — no stored token contains a dotted-quad substring of it.
        assertThat(stored).doesNotContain(RAW_IP);
        assertThat(persistence
                        .dsl()
                        .select(SECURITY_IP.IP_TOKEN)
                        .from(SECURITY_IP)
                        .fetch(SECURITY_IP.IP_TOKEN))
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
