package com.uxplima.uxmessentials.customcommands;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.customcommands.adapter.inbound.listener.CommandWarmupTracker;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The move-cancels-warmup rule for custom commands: stepping off the origin block cancels, turning your head does
 * not, quitting drops the entry, and a warmup that already completed is never armed in the first place, so nothing
 * a player does afterwards can cancel a chain that has already run.
 */
class CommandWarmupTrackerTest {

    private ServerMock server;
    private World world;
    private PlayerMock player;
    private PlayerRef who;
    private CommandWarmupTracker tracker;
    private RecordingHandle handle;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Runner");
        player.teleport(new Location(world, 10.5, 64, 10.5));
        who = BukkitRefs.toRef(player);
        tracker = new CommandWarmupTracker();
        handle = new RecordingHandle();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void movingOutOfTheOriginBlockCancelsTheWarmup() {
        tracker.arm(who, origin(), handle);

        tracker.onMove(move(11.5, 64, 10.5));

        assertThat(handle.cancelled).isTrue();
        assertThat(tracker.tracked()).isZero();
    }

    @Test
    void turningTheHeadInsideTheOriginBlockDoesNotCancel() {
        tracker.arm(who, origin(), handle);

        tracker.onMove(move(10.7, 64, 10.2));

        assertThat(handle.cancelled).isFalse();
        assertThat(tracker.tracked()).isEqualTo(1);
    }

    @Test
    void quittingDropsTheTrackedWarmup() {
        tracker.arm(who, origin(), handle);

        tracker.onQuit(new PlayerQuitEvent(player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(tracker.tracked()).isZero();
        assertThat(handle.cancelled).isFalse();
    }

    @Test
    void anAlreadyCompletedHandleIsNeverTracked() {
        handle.complete = true;

        tracker.arm(who, origin(), handle);

        assertThat(tracker.tracked()).isZero();
    }

    @Test
    void aCompletedWarmupIsForgottenSoALaterMoveNeverCancelsIt() {
        tracker.arm(who, origin(), handle);

        tracker.forget(who.uuid());
        tracker.onMove(move(20.5, 64, 20.5));

        assertThat(handle.cancelled).isFalse();
        assertThat(tracker.tracked()).isZero();
    }

    private com.uxplima.uxmessentials.shared.domain.Position origin() {
        return BukkitRefs.toPosition(player.getLocation());
    }

    private PlayerMoveEvent move(double x, double y, double z) {
        return new PlayerMoveEvent(player, player.getLocation(), new Location(world, x, y, z));
    }

    /** A handle that records the cancel rather than stopping a real countdown. */
    private static final class RecordingHandle implements WarmupHandle {

        private boolean cancelled;
        private boolean complete;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
