package com.uxplima.uxmessentials.survival.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.block.BlockBreakEvent;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.survival.adapter.inbound.listener.FastLeafDecayListener;
import com.uxplima.uxmessentials.survival.application.SurvivalConfig.LeafDecay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of fast-leaf-decay: breaking a log schedules a region-bound sweep that decays the non-persistent
 * leaves within the radius while leaving persistent (player-placed) leaves and leaves beyond the radius standing. The
 * origin log is cleared before the sweep runs, mirroring the vanilla break that clears it a tick before the delayed
 * sweep fires.
 */
class FastLeafDecayListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private CapturingScheduler scheduler;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        scheduler = new CapturingScheduler();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void breakingALogDecaysUnsupportedLeavesWithinRadiusButSparesPersistentAndDistantLeaves() {
        Block log = world.getBlockAt(0, 64, 0);
        log.setType(Material.OAK_LOG);
        Block near = leafAt(0, 65, 0, false); // one above the log, within radius
        Block side = leafAt(2, 64, 0, false); // two aside, within radius 2
        Block placed = leafAt(1, 65, 0, true); // player-placed (persistent): must survive
        Block distant = leafAt(6, 64, 0, false); // outside a radius-2 sweep: must survive
        FastLeafDecayListener listener = new FastLeafDecayListener(new LeafDecay(true, 2, 4), scheduler);

        listener.onBreak(new BlockBreakEvent(log, player));
        log.setType(Material.AIR); // the vanilla break clears the origin log before the delayed sweep runs
        scheduler.runCaptured();

        assertThat(near.getType()).isEqualTo(Material.AIR);
        assertThat(side.getType()).isEqualTo(Material.AIR);
        assertThat(placed.getType()).isEqualTo(Material.OAK_LEAVES);
        assertThat(distant.getType()).isEqualTo(Material.OAK_LEAVES);
    }

    @Test
    void breakingANonLogSchedulesNothing() {
        Block stone = world.getBlockAt(0, 64, 0);
        stone.setType(Material.STONE);
        FastLeafDecayListener listener = new FastLeafDecayListener(new LeafDecay(true, 2, 4), scheduler);

        listener.onBreak(new BlockBreakEvent(stone, player));

        assertThat(scheduler.capturedCount()).isZero();
    }

    private Block leafAt(int x, int y, int z, boolean persistent) {
        Block block = world.getBlockAt(x, y, z);
        block.setType(Material.OAK_LEAVES);
        Leaves data = (Leaves) block.getBlockData();
        data.setPersistent(persistent);
        block.setBlockData(data);
        return block;
    }

    /** Runs delayed-async work inline and captures region-scheduled tasks (the deferred leaf sweep). */
    private static final class CapturingScheduler implements Scheduler {
        private final List<Runnable> captured = new ArrayList<>();

        void runCaptured() {
            List<Runnable> tasks = List.copyOf(captured);
            captured.clear();
            tasks.forEach(Runnable::run);
        }

        int capturedCount() {
            return captured.size();
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            captured.add(task);
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
