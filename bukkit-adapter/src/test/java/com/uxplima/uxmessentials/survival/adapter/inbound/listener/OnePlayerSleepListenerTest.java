package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.bukkit.GameMode;
import org.bukkit.Location;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.survival.domain.SleepThreshold;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Coverage of one-player-sleep: a lone sleeper skips the night to morning under the default count of one, the night
 * holds when the threshold is not met, and spectators and sleep-ignored players are excluded from the eligible count.
 */
class OnePlayerSleepListenerTest {

    private static final long NIGHT = 15_000L;
    private static final long MORNING = 1_000L;

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        world.setTime(NIGHT);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aLoneSleeperSkipsTheNightUnderTheOnePlayerDefault() {
        PlayerMock sleeper = eligiblePlayer("Steve");
        OnePlayerSleepListener listener = listener(new SleepThreshold(1, 50));

        listener.advanceIfEnoughSleeping(world, sleeper.getUniqueId());

        assertThat(world.getTime()).isEqualTo(MORNING);
    }

    @Test
    void theNightHoldsWhenNotEnoughPlayersSleep() {
        PlayerMock sleeper = eligiblePlayer("Steve");
        // Two sleepers required, only the one entering the bed: the night is not skipped.
        OnePlayerSleepListener listener = listener(new SleepThreshold(2, 0));

        listener.advanceIfEnoughSleeping(world, sleeper.getUniqueId());

        assertThat(world.getTime()).isEqualTo(NIGHT);
    }

    @Test
    void spectatorsAndSleepIgnoredPlayersDoNotCountTowardTheEligibleTotal() {
        PlayerMock sleeper = eligiblePlayer("Steve");
        PlayerMock spectator = eligiblePlayer("Watcher");
        spectator.setGameMode(GameMode.SPECTATOR);
        PlayerMock afk = eligiblePlayer("Idle");
        afk.setSleepingIgnored(true);
        // 100% of the eligible players must sleep; only Steve is eligible, so his single sleep meets it.
        OnePlayerSleepListener listener = listener(new SleepThreshold(0, 100));

        listener.advanceIfEnoughSleeping(world, sleeper.getUniqueId());

        assertThat(world.getTime()).isEqualTo(MORNING);
    }

    private PlayerMock eligiblePlayer(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(new Location(world, 0.5, 64, 0.5));
        return player;
    }

    private static OnePlayerSleepListener listener(SleepThreshold threshold) {
        return new OnePlayerSleepListener(threshold, new InlineScheduler());
    }

    /** A scheduler that runs everything inline; the world advance is exercised through the package-private counter. */
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
