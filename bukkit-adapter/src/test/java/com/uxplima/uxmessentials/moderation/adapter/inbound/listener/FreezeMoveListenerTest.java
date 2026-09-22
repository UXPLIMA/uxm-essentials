package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.event.player.PlayerMoveEvent;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A frozen player is told why they cannot walk.
 *
 * <p>The listener cancelled the movement and said nothing, so a frozen player read the freeze notice once
 * (when the staff member froze them) and then met a wall in silence, while {@code moderation.freeze.blocked}
 * shipped in twelve languages and was never sent. A move event fires many times a second, so the notice is
 * throttled, and the interval is the operator's: {@code modules/moderation/config.conf} sets it and zero
 * turns the notice off.
 */
class FreezeMoveListenerTest {

    private static final Instant T0 = Instant.parse("2026-09-22T12:00:00Z");

    private ServerMock server;
    private PlayerMock player;
    private RecordingSink sink;
    private MovableClock clock;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Frozen");
        sink = new RecordingSink();
        clock = new MovableClock(T0);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFrozenPlayerWhoTriesToWalkIsToldWhy() {
        FreezeMoveListener listener = listener(Duration.ofSeconds(3), player.getUniqueId());

        listener.onMove(walk());

        assertThat(sink.keys).containsExactly(ModerationMessageKey.FREEZE_BLOCKED);
    }

    @Test
    void aHeldMovementKeyIsOneNoticeRatherThanFifty() {
        FreezeMoveListener listener = listener(Duration.ofSeconds(3), player.getUniqueId());

        listener.onMove(walk());
        listener.onMove(walk());
        listener.onMove(walk());

        assertThat(sink.keys)
                .as("a move event fires many times a second and the notice must not become the flood")
                .containsExactly(ModerationMessageKey.FREEZE_BLOCKED);
    }

    @Test
    void theNoticeComesBackOnceTheIntervalHasPassed() {
        FreezeMoveListener listener = listener(Duration.ofSeconds(3), player.getUniqueId());

        listener.onMove(walk());
        clock.advance(Duration.ofSeconds(4));
        listener.onMove(walk());

        assertThat(sink.keys).hasSize(2);
    }

    @Test
    void anIntervalOfZeroFreezesThePlayerInSilence() {
        FreezeMoveListener listener = listener(Duration.ZERO, player.getUniqueId());

        PlayerMoveEvent event = walk();
        listener.onMove(event);

        assertThat(event.isCancelled())
                .as("the freeze itself does not depend on the notice")
                .isTrue();
        assertThat(sink.keys).isEmpty();
    }

    @Test
    void lookingAroundIsNeitherCancelledNorAnswered() {
        FreezeMoveListener listener = listener(Duration.ofSeconds(3), player.getUniqueId());

        PlayerMoveEvent event = turnHead();
        listener.onMove(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(sink.keys).isEmpty();
    }

    @Test
    void aPlayerWhoIsNotFrozenIsLeftAlone() {
        FreezeMoveListener listener = listener(Duration.ofSeconds(3), UUID.randomUUID());

        PlayerMoveEvent event = walk();
        listener.onMove(event);

        assertThat(event.isCancelled()).isFalse();
        assertThat(sink.keys).isEmpty();
    }

    private FreezeMoveListener listener(Duration notice, UUID frozen) {
        Set<UUID> held = Set.of(frozen);
        return new FreezeMoveListener(held::contains, new KeyMessages(), sink, clock, notice);
    }

    /** A step from one block to the next, which is the move the freeze cancels. */
    private PlayerMoveEvent walk() {
        Location from = new Location(server.getWorld("world"), 0.5, 64, 0.5);
        Location to = new Location(server.getWorld("world"), 1.5, 64, 0.5);
        return new PlayerMoveEvent(player, from, to);
    }

    /** A look around inside one block, which the freeze leaves alone. */
    private PlayerMoveEvent turnHead() {
        Location from = new Location(server.getWorld("world"), 0.5, 64, 0.5, 0f, 0f);
        Location to = new Location(server.getWorld("world"), 0.5, 64, 0.5, 90f, 0f);
        return new PlayerMoveEvent(player, from, to);
    }

    /** A clock a test can push forward, so the throttle can be watched rather than waited out. */
    private static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // The key list is filled by KeyMessages, which resolves before this is called.
        }
    }

    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }
}
