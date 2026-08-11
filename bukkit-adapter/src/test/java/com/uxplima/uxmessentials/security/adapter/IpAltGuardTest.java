package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.security.application.port.IpGuardStore;
import com.uxplima.uxmessentials.security.domain.IpAssociation;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the same-IP alt guard on join: a join records a HASHED IP token (never the raw address); a
 * join that pushes an address over the account cap is kicked; a join that shares an address with another account
 * notifies staff; and with the guard disabled nothing is recorded and nobody is kicked.
 */
class IpAltGuardTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String RAW_IP = "203.0.113.7";

    /** The tokeniser under test, keyed with a fixed test key so a token is reproducible across assertions. */
    private static final IpHashing IP_HASHING =
            new IpHashing("test-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private ServerMock server;
    private FakeIpGuardStore store;
    private FakePlayerLookup lookup;
    private RecordingSink sink;
    private SecurityStaffNotifier notifier;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        store = new FakeIpGuardStore();
        lookup = new FakePlayerLookup();
        sink = new RecordingSink();
        notifier = new SecurityStaffNotifier(server, new InlineScheduler(), new KeyMessages(), sink, new NoopLogger());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aJoinRecordsAHashedTokenNeverTheRawIp() {
        controller(config(true, 0, true)).onJoin(player(RAW_IP));

        assertThat(store.records).hasSize(1);
        String token = store.records.get(0).ipToken();
        assertThat(token).isEqualTo(IP_HASHING.hash(RAW_IP)).doesNotContain(RAW_IP);
    }

    @Test
    void aJoinOverTheAccountCapIsKicked() {
        IpGuardController controller = controller(config(true, 1, false));
        // An earlier account already holds the address.
        store.record(UUID.randomUUID(), IP_HASHING.hash(RAW_IP), NOW);

        PlayerMock joiner = player(RAW_IP);
        controller.onJoin(joiner);

        // The joining account is the second on a cap of one, so it is refused.
        assertThat(joiner.isOnline()).isFalse();
    }

    @Test
    void aJoinWithinTheCapStays() {
        IpGuardController controller = controller(config(true, 2, false));
        store.record(UUID.randomUUID(), IP_HASHING.hash(RAW_IP), NOW);

        PlayerMock joiner = player(RAW_IP);
        controller.onJoin(joiner);

        assertThat(joiner.isOnline()).isTrue();
    }

    @Test
    void aSharedAddressNotifiesStaff() {
        UUID existing = UUID.randomUUID();
        lookup.names.put(existing, "OldAccount");
        store.record(existing, IP_HASHING.hash(RAW_IP), NOW);
        PlayerMock staff = server.addPlayer();
        staff.addAttachment(MockBukkit.createMockPlugin("Notify"), SecurityStaffNotifier.NOTIFY_NODE, true);

        controller(config(true, 0, true)).onJoin(player(RAW_IP));

        assertThat(sink.delivered).contains("security.alts.notify");
    }

    @Test
    void aDisabledGuardRecordsNothingAndKicksNobody() {
        PlayerMock joiner = player(RAW_IP);

        controller(config(false, 1, true)).onJoin(joiner);

        assertThat(store.records).isEmpty();
        assertThat(joiner.isOnline()).isTrue();
    }

    private IpGuardController controller(SecurityConfig.IpGuard config) {
        return new IpGuardController(
                store, config, lookup, notifier, IP_HASHING, new InlineScheduler(), new KeyMessages(), clock);
    }

    private PlayerMock player(String ip) {
        PlayerMock player = server.addPlayer();
        player.setAddress(new InetSocketAddress(ip, 40_000));
        return player;
    }

    private static SecurityConfig.IpGuard config(boolean enabled, int maxAccounts, boolean notifyStaff) {
        return new SecurityConfig.IpGuard(enabled, maxAccounts, notifyStaff);
    }

    /** An in-memory {@link IpGuardStore} that captures every recorded association for the assertions. */
    private static final class FakeIpGuardStore implements IpGuardStore {
        private final List<IpAssociation> records = new ArrayList<>();

        @Override
        public void record(UUID account, String ipToken, Instant seenAt) {
            records.add(new IpAssociation(account, ipToken));
        }

        @Override
        public Set<UUID> accountsOnIp(String ipToken) {
            return records.stream()
                    .filter(link -> link.ipToken().equals(ipToken))
                    .map(IpAssociation::account)
                    .collect(Collectors.toSet());
        }

        @Override
        public List<IpAssociation> sharingIpWith(UUID account) {
            return List.copyOf(records);
        }
    }

    /** A player lookup answering only from a fixed uuid-to-name map. */
    private static final class FakePlayerLookup implements PlayerLookup {
        private final Map<UUID, String> names = new HashMap<>();

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(names.get(uuid)).map(name -> new PlayerRef(uuid, name));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** Resolves every key to its dotted catalog id so a test can assert which message was delivered. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records every delivered message so a test can assert staff saw the expected line. */
    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
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

    /** Runs every scheduler hop inline so the async DB work and the kick resolve synchronously in the test. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}
