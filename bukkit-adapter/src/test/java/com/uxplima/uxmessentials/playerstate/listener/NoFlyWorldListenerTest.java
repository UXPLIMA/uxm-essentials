package com.uxplima.uxmessentials.playerstate.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.listener.NoFlyWorldListener;
import com.uxplima.uxmessentials.playerstate.application.NoFlyWorldPolicy;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
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
 * MockBukkit coverage of the no-fly-world strip-on-arrival: a survival player flying into a listed world has
 * {@code /fly} flight removed and is notified, a player with the bypass node keeps flight, and a creative
 * player's gamemode flight is never touched. The {@link Scheduler} is the inline fake so the entity-thread hop
 * runs deterministically; {@link Messages}/{@link MessageSink} are mocked so the notice is observable without
 * the real catalog.
 */
class NoFlyWorldListenerTest {

    private ServerMock server;
    private World pvp;
    private Messages messages;
    private MessageSink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        pvp = server.addSimpleWorld("pvp");
        messages = mock(Messages.class);
        sink = mock(MessageSink.class);
        when(messages.resolve(any(), any(), any())).thenReturn("no-fly");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private NoFlyWorldListener listener() {
        return new NoFlyWorldListener(new NoFlyWorldPolicy(List.of("pvp")), new InlineScheduler(), messages, sink);
    }

    private PlayerChangedWorldEvent arrival(PlayerMock player) {
        player.teleport(pvp.getSpawnLocation());
        return new PlayerChangedWorldEvent(player, server.getWorld("world"));
    }

    @Test
    void stripsPluginFlightWhenAFlyingPlayerEntersANoFlyWorld() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setGameMode(GameMode.SURVIVAL);
        alice.setAllowFlight(true);
        alice.setFlying(true);

        listener().onWorldChange(arrival(alice));

        assertThat(alice.getAllowFlight()).isFalse();
        assertThat(alice.isFlying()).isFalse();
    }

    @Test
    void leavesGamemodeFlightAlone() {
        PlayerMock builder = server.addPlayer("Builder");
        builder.setGameMode(GameMode.CREATIVE);
        builder.setAllowFlight(true);
        builder.setFlying(true);

        listener().onWorldChange(arrival(builder));

        assertThat(builder.getAllowFlight()).isTrue();
    }

    @Test
    void bypassHolderKeepsFlight() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.setGameMode(GameMode.SURVIVAL);
        staff.setAllowFlight(true);
        staff.setFlying(true);
        staff.addAttachment(MockBukkit.createMockPlugin(), NoFlyWorldListener.BYPASS_NODE, true);

        listener().onWorldChange(arrival(staff));

        assertThat(staff.getAllowFlight()).isTrue();
    }

    @Test
    void aGroundedPlayerIsLeftAlone() {
        PlayerMock walker = server.addPlayer("Walker");
        walker.setGameMode(GameMode.SURVIVAL);
        walker.setAllowFlight(false);

        listener().onWorldChange(arrival(walker));

        assertThat(walker.getAllowFlight()).isFalse();
    }

    /** A {@link Scheduler} that runs every task inline so the entity-thread hop is deterministic in tests. */
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
