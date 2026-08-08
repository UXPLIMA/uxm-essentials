package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.CombatGate;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import org.junit.jupiter.api.Test;

/**
 * A combat-tagged player may not teleport out of the fight. The rule has to hold on every self-initiated
 * route or it holds on none: {@code /home} and friends through {@link TeleportEngine#launch}, {@code /rtp}
 * through {@link TeleportEngine#gateRandom}, and {@code /tpa} through {@link RequestTeleport}. A player who
 * cannot {@code /home} out but can {@code /tpa} a friend and be pulled out has escaped just the same.
 *
 * <p>The gate is checked before the cooldown and before the warmup, so a refused teleport costs the player
 * nothing: no cooldown is stamped and no hop is issued. With {@link CombatGate#NEVER} bound, which is what a
 * server with no combat plugin gets, every route behaves exactly as it did before.
 */
class CombatGateTest {

    private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    private static final CombatGate TAGGED = who -> true;

    @Test
    void aTaggedPlayerCannotSelfTeleport() {
        RecordingExecutor executor = new RecordingExecutor();
        CapturingSink sink = new CapturingSink();

        Result<Unit, TeleportError> result =
                engine(TAGGED, executor, sink).launch(ALICE, destination(), TeleportKind.HOME);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.COMBAT_TAGGED);
        assertThat(executor.hops).isZero();
        assertThat(sink.sent).containsExactly(TeleportMessageKey.COMBAT_TAGGED.key());
    }

    @Test
    void anUntaggedPlayerTeleportsNormally() {
        RecordingExecutor executor = new RecordingExecutor();

        Result<Unit, TeleportError> result =
                engine(CombatGate.NEVER, executor, new CapturingSink()).launch(ALICE, destination(), TeleportKind.HOME);

        assertThat(result.isOk()).isTrue();
        assertThat(executor.hops).isEqualTo(1);
    }

    @Test
    void aTaggedPlayerCannotRandomTeleport() {
        CapturingSink sink = new CapturingSink();

        Result<Unit, TeleportError> result =
                engine(TAGGED, new RecordingExecutor(), sink).gateRandom(ALICE);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.COMBAT_TAGGED);
        assertThat(sink.sent).containsExactly(TeleportMessageKey.COMBAT_TAGGED.key());
    }

    @Test
    void aTaggedPlayerCannotOpenATeleportRequest() {
        CapturingSink sink = new CapturingSink();

        Result<TeleportRequest, TeleportError> result =
                tpa(TAGGED, sink).request(ALICE, BOB, RequestDirection.TO_TARGET);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.COMBAT_TAGGED);
        assertThat(sink.sent).containsExactly(TeleportMessageKey.COMBAT_TAGGED.key());
    }

    @Test
    void anUntaggedPlayerOpensATeleportRequestNormally() {
        Result<TeleportRequest, TeleportError> result =
                tpa(CombatGate.NEVER, new CapturingSink()).request(ALICE, BOB, RequestDirection.TO_TARGET);

        assertThat(result.isOk()).isTrue();
    }

    private static TeleportEngine engine(CombatGate combat, RecordingExecutor executor, CapturingSink sink) {
        return new TeleportEngine(
                new NoCooldowns(),
                new ImmediateWarmups(),
                executor,
                new Notifier(new KeyOnlyMessages(), sink),
                new NoopEvents(),
                new TeleportSettings(new MinimalConfig()),
                JailGate.NEVER,
                combat);
    }

    private static RequestTeleport tpa(CombatGate combat, CapturingSink sink) {
        return new RequestTeleport(
                new NoopRegistry(),
                new OpenFlags(),
                new Notifier(new KeyOnlyMessages(), sink),
                new NoopEvents(),
                new TeleportSettings(new MinimalConfig()),
                JailGate.NEVER,
                combat,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Destination destination() {
        WorldRef world = new WorldRef(UUID.randomUUID(), "world");
        return Destination.at(new Position(world, 0, 64, 0, 0f, 0f));
    }

    /** A config answering the request TTL the tpa use case reads, and defaults for everything else. */
    private record MinimalConfig() implements ConfigStore {
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
            return "request-ttl-seconds".equals(path)
                    ? (int) Duration.ofMinutes(1).toSeconds()
                    : fallback;
        }
    }

    /** Flags under which every request would pass, so only the combat gate can reject. */
    private record OpenFlags() implements TeleportFlags {
        @Override
        public boolean acceptsRequests(PlayerRef who) {
            return true;
        }

        @Override
        public boolean hasBlocked(PlayerRef target, PlayerRef requester) {
            return false;
        }

        @Override
        public boolean toggleRequests(PlayerRef who) {
            return true;
        }

        @Override
        public void setAcceptsRequests(PlayerRef who, boolean accepting) {}

        @Override
        public boolean autoAccepts(PlayerRef who) {
            return false;
        }

        @Override
        public boolean toggleAutoAccepts(PlayerRef who) {
            return false;
        }

        @Override
        public void block(PlayerRef blocker, PlayerRef requester) {}

        @Override
        public void unblock(PlayerRef blocker, PlayerRef requester) {}
    }

    private static final class NoCooldowns implements Cooldowns {
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
            return new WarmupHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isComplete() {
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    private static final class RecordingExecutor implements TeleportExecutor {
        int hops;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            hops++;
        }
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoopRegistry implements RequestRegistry {
        @Override
        public void store(TeleportRequest request) {}

        @Override
        public Optional<TeleportRequest> byId(RequestId id) {
            return Optional.empty();
        }

        @Override
        public List<TeleportRequest> pendingFor(PlayerRef target) {
            return List.of();
        }

        @Override
        public Optional<TeleportRequest> outgoing(PlayerRef requester) {
            return Optional.empty();
        }

        @Override
        public void remove(RequestId id) {}
    }

    private static final class KeyOnlyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class CapturingSink implements MessageSink {
        private final List<String> sent = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            sent.add(renderedText);
        }
    }
}
