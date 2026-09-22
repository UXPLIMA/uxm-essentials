package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelToggles;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A warmup that really begins says so.
 *
 * <p>The engine wires the cancel callback and nothing announced the start, so a player who ran a command
 * with a warmup stood still for three seconds with no idea why, and the line written for it
 * ({@code teleport.warmup.started}) was shipped in ten languages and never sent. The notice belongs here
 * rather than in the engine because this is where the real length is known: the engine reads the config
 * default, and the tier node a player holds is resolved in this decorator.
 */
class TrackingWarmupsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aWarmupThatBeginsTellsThePlayerHowLongToStandStill() {
        PlayerMock player = server.addPlayer();
        RecordingSink sink = new RecordingSink();
        TrackingWarmups warmups = warmups(sink, new PendingDelegate(), new FixedPermissions(null));

        warmups.begin(BukkitRefs.toRef(player), new Warmups.WarmupKind("teleport", 3), () -> {}, () -> {});

        assertThat(sink.delivered)
                .as("the player is asked to stand still, so they have to be told for how long")
                .containsExactly(TeleportMessageKey.WARMUP_STARTED.key() + " seconds=3");
    }

    @Test
    void theNoticeCarriesTheLengthTheTierNodeResolvedRatherThanTheConfigDefault() {
        PlayerMock player = server.addPlayer();
        RecordingSink sink = new RecordingSink();
        TrackingWarmups warmups = warmups(sink, new PendingDelegate(), new FixedPermissions(1L));

        warmups.begin(BukkitRefs.toRef(player), new Warmups.WarmupKind("teleport", 3), () -> {}, () -> {});

        assertThat(sink.delivered).containsExactly(TeleportMessageKey.WARMUP_STARTED.key() + " seconds=1");
    }

    @Test
    void aWarmupThatIsAlreadyOverSaysNothing() {
        PlayerMock player = server.addPlayer();
        RecordingSink sink = new RecordingSink();
        PlayerRef who = BukkitRefs.toRef(player);
        TrackingWarmups warmups = warmups(sink, new ImmediateDelegate(who), new FixedPermissions(null));

        warmups.begin(who, new Warmups.WarmupKind("teleport", 0), () -> {}, () -> {});

        assertThat(sink.delivered)
                .as("a bypassed warmup never makes anybody wait, so there is nothing to announce")
                .isEmpty();
    }

    private TrackingWarmups warmups(RecordingSink sink, Warmups delegate, Permissions permissions) {
        return new TrackingWarmups(
                delegate,
                new WarmupTracker(),
                WarmupCancelToggles::defaults,
                permissions,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                new Notifier(new KeyAndPlaceholders(), sink));
    }

    /** A delegate whose warmup is still counting down, which is the only case that announces anything. */
    private static final class PendingDelegate implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            return new PendingHandle();
        }
    }

    /** A warmup in flight: not complete, not cancelled, which is what the player is waiting through. */
    private static final class PendingHandle implements Warmups.WarmupHandle {
        @Override
        public void cancel() {
            // Nothing in this test cancels; the handle exists so the decorator sees a live warmup.
        }

        @Override
        public boolean isComplete() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    /** A delegate that completes at once, the bypass a tier node buys. */
    private record ImmediateDelegate(PlayerRef who) implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef ignored, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new CompletedWarmup(who);
        }
    }

    /** Resolves a key to its path and its placeholders, so the assertion reads what the player was told. */
    private static final class KeyAndPlaceholders implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            StringBuilder rendered = new StringBuilder(key.key());
            placeholders.forEach((name, value) ->
                    rendered.append(' ').append(name).append('=').append(value));
            return rendered.toString();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    /** Answers one resolved warmup length, or none, in which case the config default stands. */
    private record FixedPermissions(@Nullable Long seconds) implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return seconds == null ? new QuotaResult.Limited(configDefault) : new QuotaResult.Limited(seconds);
        }
    }
}
