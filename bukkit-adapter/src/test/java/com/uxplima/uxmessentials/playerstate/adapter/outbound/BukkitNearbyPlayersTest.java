package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;

import com.uxplima.uxmessentials.playerstate.application.port.NearbyPlayers;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
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
 * MockBukkit coverage of the {@code /near} adapter. The scan is push-shaped: {@code within} schedules the
 * whole roster read onto the global region thread and hands the result to a callback, never returning a list
 * and never blocking the calling region thread on a foreign read. These tests pin that the work runs through
 * {@link Scheduler#onGlobal}, that the callback receives the radius-filtered, nearest-first result, and that
 * the scheduler is exercised with no blocking marshal ({@code .get()}/{@code .join()}) in the flow.
 */
class BukkitNearbyPlayersTest {

    private ServerMock server;
    private RecordingScheduler scheduler;
    private BukkitNearbyPlayers nearby;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        scheduler = new RecordingScheduler();
        nearby = new BukkitNearbyPlayers(scheduler);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesOnTheGlobalThreadAndPushesTheNearestFirstResult() {
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.teleport(new Location(server.getWorld("world"), 0, 64, 0));
        PlayerMock far = server.addPlayer("Far");
        far.teleport(new Location(server.getWorld("world"), 30, 64, 0));
        PlayerMock near = server.addPlayer("Near");
        near.teleport(new Location(server.getWorld("world"), 5, 64, 0));

        List<NearbyPlayers.Nearby> pushed = new ArrayList<>();
        nearby.within(BukkitRefs.toRef(viewer), 100, pushed::addAll);

        // The roster read ran on the global region thread, exactly once, with no blocking marshal in the flow.
        assertThat(scheduler.globalRuns.get()).isEqualTo(1);
        // Nearest-first: Near (5 blocks) before Far (30 blocks); the viewer is excluded.
        assertThat(pushed).extracting(n -> n.who().name()).containsExactly("Near", "Far");
    }

    @Test
    void excludesPlayersBeyondTheRadius() {
        PlayerMock viewer = server.addPlayer("Viewer");
        viewer.teleport(new Location(server.getWorld("world"), 0, 64, 0));
        PlayerMock outside = server.addPlayer("Outside");
        outside.teleport(new Location(server.getWorld("world"), 500, 64, 0));

        List<NearbyPlayers.Nearby> pushed = new ArrayList<>();
        nearby.within(BukkitRefs.toRef(viewer), 100, pushed::addAll);

        assertThat(scheduler.globalRuns.get()).isEqualTo(1);
        assertThat(pushed).isEmpty();
    }

    @Test
    void anOfflineViewerPushesAnEmptyList() {
        PlayerMock viewer = server.addPlayer("Viewer");
        PlayerRef ref = BukkitRefs.toRef(viewer);
        viewer.disconnect();

        AtomicBoolean pushed = new AtomicBoolean(false);
        nearby.within(ref, 100, found -> {
            pushed.set(true);
            assertThat(found).isEmpty();
        });

        assertThat(scheduler.globalRuns.get()).isEqualTo(1);
        assertThat(pushed).isTrue();
    }

    /** Runs scheduled work inline and counts global hops, so the test sees the resolve land on {@code onGlobal}. */
    private static final class RecordingScheduler implements Scheduler {
        private final AtomicInteger globalRuns = new AtomicInteger();

        @Override
        public void onGlobal(Runnable task) {
            globalRuns.incrementAndGet();
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
