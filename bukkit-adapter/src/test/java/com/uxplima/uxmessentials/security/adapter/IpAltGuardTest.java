package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.security.application.SecurityConfig;
import com.uxplima.uxmessentials.shared.adapter.inbound.ip.IpHistoryRecorder;
import com.uxplima.uxmessentials.shared.adapter.outbound.IpHashing;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.IpHistoryStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.IpAssociation;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the join-time capture and the same-IP alt guard that watches it: the kernel recorder
 * writes a HASHED IP token (never the raw address unless something asks it to retain one), and the guard, which
 * captures nothing of its own, then kicks a join that pushes an address over the account cap and notifies staff
 * about a shared address. With the guard disabled the capture still happens, because the history is shared with
 * moderation, but nobody is kicked and nobody is told.
 */
class IpAltGuardTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final String RAW_IP = "203.0.113.7";

    /** The tokeniser under test, keyed with a fixed test key so a token is reproducible across assertions. */
    private static final IpHashing IP_HASHING =
            new IpHashing("test-key".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private ServerMock server;
    private FakeIpHistoryStore store;
    private FakePlayerLookup lookup;
    private RecordingSink sink;
    private SecurityStaffNotifier notifier;
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        store = new FakeIpHistoryStore();
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
        join(player(RAW_IP), config(true, 0, true), false);

        assertThat(store.rows).hasSize(1);
        Row row = store.rows.values().iterator().next();
        assertThat(row.link().ipToken()).isEqualTo(IP_HASHING.tokenFor(RAW_IP)).doesNotContain(RAW_IP);
        // Nothing on this server retains addresses, so the row carries the token alone.
        assertThat(row.address()).isNull();
    }

    @Test
    void theAddressIsKeptOnlyWhenSomethingConsumesIt() {
        // Moderation enabled: /seenip and a STRICT ban need the address itself, so the recorder retains it.
        join(player(RAW_IP), config(true, 0, true), true);

        assertThat(store.addressesOf(store.rows.keySet().iterator().next().account()))
                .containsExactly(RAW_IP);
    }

    @Test
    void aJoinOverTheAccountCapIsKicked() {
        // An earlier account already holds the address.
        store.record(UUID.randomUUID(), IP_HASHING.tokenFor(RAW_IP), null, NOW);

        PlayerMock joiner = player(RAW_IP);
        join(joiner, config(true, 1, false), false);

        // The joining account is the second on a cap of one, so it is refused.
        assertThat(joiner.isOnline()).isFalse();
    }

    @Test
    void aJoinWithinTheCapStays() {
        store.record(UUID.randomUUID(), IP_HASHING.tokenFor(RAW_IP), null, NOW);

        PlayerMock joiner = player(RAW_IP);
        join(joiner, config(true, 2, false), false);

        assertThat(joiner.isOnline()).isTrue();
    }

    @Test
    void aSharedAddressNotifiesStaff() {
        UUID existing = UUID.randomUUID();
        lookup.names.put(existing, "OldAccount");
        store.record(existing, IP_HASHING.tokenFor(RAW_IP), null, NOW);
        PlayerMock staff = server.addPlayer();
        staff.addAttachment(MockBukkit.createMockPlugin("Notify"), SecurityStaffNotifier.NOTIFY_NODE, true);

        join(player(RAW_IP), config(true, 0, true), false);

        assertThat(sink.delivered).contains("security.alts.notify");
    }

    @Test
    void aDisabledGuardKicksNobodyAndTellsNobodyThoughTheJoinIsStillRecorded() {
        store.record(UUID.randomUUID(), IP_HASHING.tokenFor(RAW_IP), null, NOW);
        PlayerMock joiner = player(RAW_IP);

        join(joiner, config(false, 1, true), false);

        // The capture is shared with moderation's alt history, so it is not the guard's to switch off.
        assertThat(store.accountsOnToken(IP_HASHING.tokenFor(RAW_IP))).hasSize(2);
        assertThat(joiner.isOnline()).isTrue();
        assertThat(sink.delivered).isEmpty();
    }

    /** Drive one join through the recorder with the guard watching it, exactly as the wiring registers them. */
    private void join(PlayerMock joiner, SecurityConfig.IpGuard config, boolean retainAddress) {
        IpHistoryRecorder recorder =
                new IpHistoryRecorder(store, IP_HASHING, new InlineScheduler(), clock, retainAddress);
        recorder.observe(
                new IpGuardController(store, config, lookup, notifier, new InlineScheduler(), new KeyMessages()));
        recorder.onJoin(new org.bukkit.event.player.PlayerJoinEvent(joiner, Component.empty()));
    }

    private PlayerMock player(String ip) {
        PlayerMock player = server.addPlayer();
        player.setAddress(new InetSocketAddress(ip, 40_000));
        return player;
    }

    private static SecurityConfig.IpGuard config(boolean enabled, int maxAccounts, boolean notifyStaff) {
        return new SecurityConfig.IpGuard(enabled, maxAccounts, notifyStaff);
    }

    private record Row(IpAssociation link, @Nullable String address) {}

    /** An in-memory {@link IpHistoryStore} that keeps every recorded row for the assertions. */
    private static final class FakeIpHistoryStore implements IpHistoryStore {
        private final Map<IpAssociation, Row> rows = new LinkedHashMap<>();

        @Override
        public void record(UUID account, String ipToken, @Nullable String address, Instant seenAt) {
            IpAssociation link = new IpAssociation(account, ipToken);
            Row existing = rows.get(link);
            String kept = address != null ? address : existing == null ? null : existing.address();
            rows.put(link, new Row(link, kept));
        }

        @Override
        public Set<UUID> accountsOnToken(String ipToken) {
            return rows.keySet().stream()
                    .filter(link -> link.ipToken().equals(ipToken))
                    .map(IpAssociation::account)
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public List<IpAssociation> sharingTokenWith(UUID account) {
            Set<String> own = rows.keySet().stream()
                    .filter(link -> link.account().equals(account))
                    .map(IpAssociation::ipToken)
                    .collect(Collectors.toUnmodifiableSet());
            return rows.keySet().stream()
                    .filter(link -> own.contains(link.ipToken()))
                    .toList();
        }

        @Override
        public Set<String> addressesOf(UUID account) {
            return rows.entrySet().stream()
                    .filter(entry -> entry.getKey().account().equals(account))
                    .map(entry -> entry.getValue().address())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
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
