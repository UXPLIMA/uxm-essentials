package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerwarps.application.PlayerWarpNotifier;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleport;
import com.uxplima.uxmessentials.playerwarps.application.port.PendingTeleportStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.VisitSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEffects;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The send half of a cross-server teleport, {@link BukkitCrossServerTeleport}, over a MockBukkit server so an
 * online player can be resolved and connected. It proves the two runtime branches: with the proxy channel
 * available the intent is recorded (carrying the exact charge) and a {@code Connect} is issued to the warp's
 * backend; with the channel unavailable nothing is recorded, the player is told the warp is unreachable, and the
 * charge is refunded. The connector and store are recording fakes and the scheduler runs its region hop inline.
 */
class BukkitCrossServerTeleportTest {

    private static final WorldRef WORLD = new WorldRef(new UUID(4L, 4L), "world");
    private static final String LOCAL = "lobby";
    private static final Instant NOW = Instant.parse("2026-07-10T00:00:00Z");

    private ServerMock server;
    private RecordingConnector connector;
    private FakePendingStore store;
    private FakeEconomy economy;
    private RecordingSink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        connector = new RecordingConnector(true);
        store = new FakePendingStore();
        economy = new FakeEconomy();
        sink = new RecordingSink();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anAvailableProxyRecordsThePendingRowAndConnectsToTheTargetBackend() {
        PlayerMock player = server.addPlayer("Visitor");
        PlayerRef who = BukkitRefs.toRef(player);
        WarpCost charge = WarpCost.of(new BigDecimal("120"), "coins");

        teleport().send(who, remoteWarp("survival"), Optional.of(charge));

        assertThat(connector.servers).containsExactly("survival");
        PendingTeleport row = store.find(who.uuid()).orElseThrow();
        assertThat(row.targetServer()).isEqualTo("survival");
        assertThat(row.originServer()).isEqualTo(LOCAL);
        assertThat(row.warp()).isEqualTo(PlayerWarpId.of(5L));
        assertThat(row.paid()).contains(charge);
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.cross-server.sending"));
        assertThat(economy.refunds).as("an available send never refunds").isEmpty();
    }

    @Test
    void anUnavailableProxyRefusesTheHopAndRefundsWithoutRecordingOrConnecting() {
        connector = new RecordingConnector(false);
        PlayerMock player = server.addPlayer("Visitor");
        PlayerRef who = BukkitRefs.toRef(player);
        WarpCost charge = WarpCost.of(new BigDecimal("120"), "coins");

        teleport().send(who, remoteWarp("survival"), Optional.of(charge));

        assertThat(connector.servers)
                .as("an unavailable proxy is never asked to connect")
                .isEmpty();
        assertThat(store.find(who.uuid()))
                .as("no intent is recorded when the send cannot leave")
                .isEmpty();
        assertThat(sink.delivered).anyMatch(text -> text.startsWith("pwarp.cross-server.unavailable"));
        assertThat(economy.refunds).containsExactly(new BigDecimal("120"));
    }

    @Test
    void afreeRemoteWarpRecordsAnEmptyChargeAndStillConnects() {
        PlayerMock player = server.addPlayer("Visitor");
        PlayerRef who = BukkitRefs.toRef(player);

        teleport().send(who, remoteWarp("survival"), Optional.empty());

        assertThat(connector.servers).containsExactly("survival");
        assertThat(store.find(who.uuid()).orElseThrow().paid()).isEmpty();
        assertThat(economy.refunds).isEmpty();
    }

    private BukkitCrossServerTeleport teleport() {
        return new BukkitCrossServerTeleport(
                store,
                connector,
                new InlineScheduler(),
                Optional.of(economy),
                new PlayerWarpNotifier(new KeyMessages(), sink),
                LOCAL,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PlayerWarp remoteWarp(String serverId) {
        return new PlayerWarp(
                Optional.of(PlayerWarpId.of(5L)),
                new PlayerRef(new UUID(9L, 9L), "Owner"),
                "Owner",
                PlayerWarpName.of("base"),
                Optional.empty(),
                Position.of(WORLD, 8, 64, 8),
                Optional.of(serverId),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                WarpAccess.PUBLIC,
                false,
                WarpStatus.ACTIVE,
                WarpCost.free(),
                WarpEarnings.zero("default"),
                RatingSummary.empty(),
                VisitSummary.empty(),
                0,
                Optional.empty(),
                Optional.empty(),
                WarpEffects.none(),
                WarpTimingOverrides.none(),
                NOW,
                NOW);
    }

    private static final class RecordingConnector implements ServerConnector {
        private final boolean available;
        private final List<String> servers = new ArrayList<>();

        RecordingConnector(boolean available) {
            this.available = available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void connect(Player player, String server) {
            servers.add(server);
        }
    }

    private static final class FakePendingStore implements PendingTeleportStore {
        private final Map<UUID, PendingTeleport> rows = new HashMap<>();

        @Override
        public void record(PendingTeleport pending) {
            rows.put(pending.player(), pending);
        }

        @Override
        public Optional<PendingTeleport> find(UUID player) {
            return Optional.ofNullable(rows.get(player));
        }

        @Override
        public void clear(UUID player) {
            rows.remove(player);
        }
    }

    private static final class FakeEconomy implements PlayerWarpEconomy {
        final List<BigDecimal> refunds = new ArrayList<>();

        @Override
        public Result<Unit, ChargeError> chargeAndAccrue(
                PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
            return Result.ok();
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
            refunds.add(amount);
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> collectRent(
                PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId) {
            return Result.ok();
        }
    }

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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key() + " " + placeholders;
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }
}
