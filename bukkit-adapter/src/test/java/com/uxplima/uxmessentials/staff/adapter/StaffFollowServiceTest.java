package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffFollowService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * {@link StaffFollowService}: toggling starts then stops a session, a tick teleports the staff onto a live
 * target, and a tick auto-ends a session whose target has gone offline or whose staff has left staff mode.
 */
class StaffFollowServiceTest {

    private ServerMock server;
    private PlayerMock staff;
    private PlayerMock target;
    private Set<UUID> inMode;
    private StaffFollowService follow;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        staff = server.addPlayer("Staff");
        target = server.addPlayer("Target");
        inMode = new HashSet<>();
        inMode.add(staff.getUniqueId());
        follow = new StaffFollowService(
                server, new ImmediateScheduler(), StaffAdapterFakes.notifier(), inMode::contains, 10);
    }

    @AfterEach
    void tearDown() {
        follow.shutdown();
        MockBukkit.unmock();
    }

    @Test
    void toggleStartsThenStops() {
        assertThat(follow.toggle(staff, target)).isTrue();
        assertThat(follow.isFollowing(staff.getUniqueId())).isTrue();

        assertThat(follow.toggle(staff, target)).isFalse();
        assertThat(follow.isFollowing(staff.getUniqueId())).isFalse();
    }

    @Test
    void aTickTeleportsTheStaffOntoTheTarget() {
        target.teleport(target.getLocation().add(50, 0, 50));
        follow.toggle(staff, target);

        follow.tick();

        assertThat(staff.getLocation().getBlockX())
                .isEqualTo(target.getLocation().getBlockX());
        assertThat(staff.getLocation().getBlockZ())
                .isEqualTo(target.getLocation().getBlockZ());
    }

    @Test
    void aTickEndsTheSessionWhenTheTargetGoesOffline() {
        follow.toggle(staff, target);
        target.disconnect();

        follow.tick();

        assertThat(follow.isFollowing(staff.getUniqueId())).isFalse();
    }

    @Test
    void aTickEndsTheSessionWhenTheStaffHasLeftMode() {
        follow.toggle(staff, target);
        inMode.remove(staff.getUniqueId());

        follow.tick();

        assertThat(follow.isFollowing(staff.getUniqueId())).isFalse();
    }

    @Test
    void stopEndsASession() {
        follow.toggle(staff, target);

        follow.stop(staff.getUniqueId());

        assertThat(follow.isFollowing(staff.getUniqueId())).isFalse();
    }

    /** A scheduler that runs everything inline and hands back a no-op repeating handle. */
    private static final class ImmediateScheduler implements Scheduler {
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}
