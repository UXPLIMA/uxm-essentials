package com.uxplima.uxmessentials.servertweaks.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.servertweaks.adapter.outbound.ServerBrandSender;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
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
 * MockBukkit coverage of the F3-brand join listener through a real {@link PlayerJoinEvent}: when the tweak is on the
 * configured brand is delivered to the joiner via the {@link ServerBrandSender} seam and re-sent once a beat later
 * (catching clients that adopt the brand just after join), and when it is off the listener is a strict no-op: the
 * gate-off-means-nothing-happens guarantee the whole module rests on. The delayed resend is skipped for a player who
 * has already left.
 */
class ServerBrandJoinListenerTest {

    private ServerMock server;
    private CapturingScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new CapturingScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enabledSendsTheBrandOnJoin() {
        PlayerMock player = server.addPlayer("Steve");
        RecordingSender sender = new RecordingSender();
        ServerBrandJoinListener listener = new ServerBrandJoinListener(true, sender, scheduler);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        // The immediate send lands on join; the resend is still parked in the scheduler.
        assertThat(sender.recipients).containsExactly(player);
    }

    @Test
    void resendsTheBrandAfterJoinWhileStillOnline() {
        PlayerMock player = server.addPlayer("Steve");
        RecordingSender sender = new RecordingSender();
        ServerBrandJoinListener listener = new ServerBrandJoinListener(true, sender, scheduler);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
        scheduler.runCaptured(); // the deferred resend lands a beat later on the player's entity thread

        assertThat(sender.recipients).containsExactly(player, player);
    }

    @Test
    void doesNotResendAfterThePlayerLeaves() {
        PlayerMock player = server.addPlayer("Steve");
        RecordingSender sender = new RecordingSender();
        ServerBrandJoinListener listener = new ServerBrandJoinListener(true, sender, scheduler);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
        player.disconnect();
        scheduler.runCaptured(); // the parked resend fires, but the player is gone

        assertThat(sender.recipients).containsExactly(player); // only the immediate send, no resend
    }

    @Test
    void disabledSendsNothing() {
        PlayerMock player = server.addPlayer("Steve");
        RecordingSender sender = new RecordingSender();
        ServerBrandJoinListener listener = new ServerBrandJoinListener(false, sender, scheduler);

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));
        scheduler.runCaptured();

        assertThat(sender.recipients).isEmpty();
    }

    /** A {@link ServerBrandSender} that records every player it was asked to send the brand to. */
    private static final class RecordingSender implements ServerBrandSender {
        private final List<Player> recipients = new ArrayList<>();

        @Override
        public void send(Player player) {
            recipients.add(player);
        }
    }

    /**
     * A scheduler that runs the async delay through immediately but captures the entity-thread task, so a test can fire
     * the deferred resend on demand. Mirrors the listener's {@code asyncAfter(delay, () -> onEntity(ref, resend))} shape.
     */
    private static final class CapturingScheduler implements Scheduler {
        private @Nullable Runnable captured;

        void runCaptured() {
            if (captured != null) {
                captured.run();
            }
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            captured = task;
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
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
