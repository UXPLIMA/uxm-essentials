package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.outbound.InMemorySpawnDirectory;
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
import com.uxplima.uxmessentials.teleport.application.ResolveSpawn;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.SafeLocationQueue;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class SpawnJoinListenerTest {

    private ServerMock server;
    private FakeQueue queue;
    private RecordingExecutor executor;
    private Notifier notifier;
    private TeleportEngine engine;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        queue = new FakeQueue();
        executor = new RecordingExecutor();
        notifier = new Notifier(new NoopMessages(), new NoopSink());
        TeleportSettings defaults = new TeleportSettings(new JoinConfig());
        engine = new TeleportEngine(
                new NoopCooldowns(),
                new ImmediateWarmups(),
                executor,
                notifier,
                new NoopEvents(),
                defaults,
                JailGate.NEVER,
                TeleportFee.FREE,
                ArrivalGrace.NONE);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void firstJoinRtpTakesPrecedenceOverSpawn() {
        PlayerMock player = server.addPlayer();
        queue.next =
                Optional.of(new RtpSafeLocation(Position.of(worldRef(player), 200, 70, 260), 320.0, Instant.EPOCH));

        listener(config(true, true, false), false).onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(queue.polls).isEqualTo(1);
        assertThat(executor.hops).isEqualTo(1);
        assertThat(executor.lastKind).isEqualTo(TeleportKind.RANDOM);
    }

    @Test
    void defaultFirstJoinPolicyUsesResolvedSetspawn() {
        PlayerMock player = server.addPlayer();

        listener(config(false, true, false), false).onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(queue.polls).isZero();
        assertThat(executor.hops).isEqualTo(1);
        assertThat(executor.lastKind).isEqualTo(TeleportKind.SPAWN);
        assertThat(java.util.Objects.requireNonNull(executor.lastDestination)
                        .position()
                        .x())
                .isEqualTo(12);
    }

    @Test
    void disabledJoinPoliciesLeaveThePlayerUntouched() {
        PlayerMock player = server.addPlayer();

        listener(config(false, false, false), false).onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(queue.polls).isZero();
        assertThat(executor.hops).isZero();
    }

    @Test
    void exemptionPermissionSuppressesAllAutomaticJoinMovement() {
        PlayerMock player = server.addPlayer();
        queue.next =
                Optional.of(new RtpSafeLocation(Position.of(worldRef(player), 200, 70, 260), 320.0, Instant.EPOCH));

        listener(config(true, true, true), true).onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(queue.polls).isZero();
        assertThat(executor.hops).isZero();
    }

    private SpawnJoinListener listener(JoinConfig config, boolean exempt) {
        TeleportSettings settings = new TeleportSettings(config);
        WorldLookup worlds = new AnyWorldLookup();
        InMemorySpawnDirectory spawns = new InMemorySpawnDirectory();
        WorldRef world = new WorldRef(server.getWorld("world").getUID(), "world");
        spawns.setDefaultSpawn(world, Position.of(world, 12, 70, 13));
        ResolveSpawn resolveSpawn = new ResolveSpawn(spawns, worlds, engine, notifier);
        ResolveRtp resolveRtp = new ResolveRtp(queue, worlds, engine, notifier, settings);
        return new SpawnJoinListener(settings, resolveSpawn, resolveRtp, executor, new FixedPermissions(exempt));
    }

    private static JoinConfig config(boolean rtp, boolean firstJoin, boolean everyJoin) {
        JoinConfig config = new JoinConfig();
        config.booleans.put("rtp.rtp-on-first-join", rtp);
        config.booleans.put("spawn.first-join", firstJoin);
        config.booleans.put("spawn.every-join", everyJoin);
        return config;
    }

    private static WorldRef worldRef(PlayerMock player) {
        return new WorldRef(player.getWorld().getUID(), player.getWorld().getName());
    }

    private static final class JoinConfig implements ConfigStore {
        private final Map<String, Boolean> booleans = new HashMap<>();

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return booleans.getOrDefault(path, fallback);
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return fallback;
        }
    }

    private static final class FakeQueue implements SafeLocationQueue {
        private Optional<RtpSafeLocation> next = Optional.empty();
        private int polls;

        @Override
        public Optional<RtpSafeLocation> poll(WorldRef world) {
            polls++;
            return next;
        }

        @Override
        public Optional<RtpSafeLocation> urgentSearch(WorldRef world) {
            return poll(world);
        }

        @Override
        public boolean hasQueue(WorldRef world) {
            return true;
        }

        @Override
        public void requestRefill(WorldRef world) {}
    }

    private static final class RecordingExecutor implements TeleportExecutor {
        private int hops;
        private @Nullable TeleportKind lastKind;
        private @Nullable Destination lastDestination;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            hops++;
            lastKind = kind;
            lastDestination = destination;
        }
    }

    private static final class FixedPermissions implements Permissions {
        private final boolean exempt;

        private FixedPermissions(boolean exempt) {
            this.exempt = exempt;
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return exempt;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class AnyWorldLookup implements WorldLookup {
        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<WorldRef> findByUid(java.util.UUID uid) {
            return Optional.empty();
        }
    }

    private static final class NoopCooldowns implements Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class ImmediateWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new CompletedWarmup(who);
        }
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoopMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}
