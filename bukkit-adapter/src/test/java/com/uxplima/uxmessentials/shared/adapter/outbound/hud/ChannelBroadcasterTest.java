package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.Set;

import org.bukkit.Sound;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
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
 * MockBukkit coverage of the shared {@link ChannelBroadcaster}: a CHAT render reaches a recipient, a render
 * returning {@code null} skips that player, the action-bar/title/boss-bar channels plus a sound do not throw, and
 * the boss-bar hide is scheduled (an {@code asyncAfter} is recorded). The scheduler runs hops inline and records
 * the deferred boss-bar hide so the test can assert it was scheduled.
 */
class ChannelBroadcasterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PlayerMock alice;
    private RecordingScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        alice = server.addPlayer("Alice");
        scheduler = new RecordingScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void chatRenderReachesARecipient() {
        ChannelBroadcaster broadcaster = new ChannelBroadcaster(scheduler, display());

        broadcaster.broadcast(
                player -> Component.text("hello " + player.getName()), Set.of(BroadcastChannel.CHAT), null);

        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("hello Alice");
    }

    @Test
    void aRenderReturningNullSkipsThatPlayer() {
        ChannelBroadcaster broadcaster = new ChannelBroadcaster(scheduler, display());

        broadcaster.broadcast(player -> null, Set.of(BroadcastChannel.CHAT), null);

        assertThat(alice.nextComponentMessage()).isNull();
    }

    @Test
    void titleActionBarAndSoundDoNotThrow() {
        @Nullable Sound sound = BukkitRegistryKeys.resolveSound("entity.player.levelup");
        ChannelBroadcaster broadcaster = new ChannelBroadcaster(scheduler, display());
        Set<BroadcastChannel> all =
                Set.of(BroadcastChannel.ACTION_BAR, BroadcastChannel.TITLE, BroadcastChannel.SUBTITLE);

        assertThatCode(() -> broadcaster.broadcast(player -> Component.text("hi"), all, sound))
                .doesNotThrowAnyException();
    }

    @Test
    void aBossBarBroadcastSchedulesAHide() {
        ChannelBroadcaster broadcaster = new ChannelBroadcaster(scheduler, display());

        broadcaster.broadcast(player -> Component.text("boss"), Set.of(BroadcastChannel.BOSS_BAR), null);

        // The boss bar was shown immediately and a delayed hide was scheduled through the Scheduler port.
        assertThat(scheduler.asyncAfterCalls).isEqualTo(1);
    }

    @Test
    void deliverOneTargetsASinglePlayer() {
        server.addPlayer("Bob");
        ChannelBroadcaster broadcaster = new ChannelBroadcaster(scheduler, display());

        broadcaster.deliverOne(
                new PlayerRef(alice.getUniqueId(), alice.getName()),
                Component.text("preview"),
                Set.of(BroadcastChannel.CHAT),
                null);

        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("preview");
    }

    private static ChannelDisplay display() {
        return new ChannelDisplay(100, 500, 100, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS, 1);
    }

    /** Runs hops inline; counts each {@code asyncAfter} so a scheduled boss-bar hide is observable, and runs it. */
    private static final class RecordingScheduler implements Scheduler {
        private int asyncAfterCalls;

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
            asyncAfterCalls++;
            task.run();
        }
    }
}
