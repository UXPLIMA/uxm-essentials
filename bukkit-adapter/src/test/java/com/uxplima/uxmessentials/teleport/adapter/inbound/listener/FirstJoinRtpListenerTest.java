package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
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
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the first-join RTP path: a genuinely first-time join, when the toggle is on, serves
 * a location O(1) from the pre-warmed pool through the async engine (a plain poll — never a synchronous
 * search) and teleports immediately; a drained pool kicks a refill and leaves the player at spawn; and the
 * toggle being off is a no-op.
 */
class FirstJoinRtpListenerTest {

    private ServerMock server;
    private FakeQueue queue;
    private RecordingExecutor executor;
    private ResolveRtp resolveRtp;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        queue = new FakeQueue();
        executor = new RecordingExecutor();
        TeleportSettings settings = new TeleportSettings(new EmptyConfig());
        Notifier notifier = new Notifier(new NoopMessages(), new NoopSink());
        TeleportEngine engine = new TeleportEngine(
                new NoopCooldowns(),
                new ImmediateWarmups(),
                executor,
                notifier,
                new NoopEvents(),
                settings,
                JailGate.NEVER,
                TeleportFee.FREE,
                ArrivalGrace.NONE);
        resolveRtp = new ResolveRtp(queue, new AnyWorldLookup(), engine, notifier, settings);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFirstTimeJoinServesFromThePoolAndTeleportsImmediately() {
        PlayerMock player = server.addPlayer();
        queue.next =
                Optional.of(new RtpSafeLocation(Position.of(worldRef(player), 200, 70, 260), 320.0, Instant.EPOCH));

        listener(true).onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(queue.polls).isEqualTo(1); // O(1) serve from the pool, never a synchronous search
        assertThat(executor.hops).isEqualTo(1); // immediate hop, no warmup
    }

    @Test
    void aDrainedPoolKicksARefillAndLeavesThePlayerPut() {
        PlayerMock player = server.addPlayer();
        queue.next = Optional.empty();

        listener(true).onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(queue.polls).isEqualTo(1);
        assertThat(queue.refills).isEqualTo(1);
        assertThat(executor.hops).isZero();
    }

    @Test
    void theToggleBeingOffIsANoOp() {
        PlayerMock player = server.addPlayer();
        queue.next =
                Optional.of(new RtpSafeLocation(Position.of(worldRef(player), 200, 70, 260), 320.0, Instant.EPOCH));

        listener(false).onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(queue.polls).isZero();
        assertThat(executor.hops).isZero();
    }

    private FirstJoinRtpListener listener(boolean enabled) {
        return new FirstJoinRtpListener(resolveRtp, () -> enabled);
    }

    private static WorldRef worldRef(PlayerMock player) {
        return new WorldRef(player.getWorld().getUID(), player.getWorld().getName());
    }

    private static final class FakeQueue implements SafeLocationQueue {
        private Optional<RtpSafeLocation> next = Optional.empty();
        private int polls;
        private int refills;

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
        public void requestRefill(WorldRef world) {
            refills++;
        }
    }

    private static final class RecordingExecutor implements TeleportExecutor {
        private int hops;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            hops++;
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

    private record EmptyConfig() implements ConfigStore {
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

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return fallback;
        }
    }
}
