package com.uxplima.uxmessentials.vote.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collection;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * MockBukkit coverage of {@link BukkitVoteAudience}: the roster snapshot is taken on the global region thread.
 * Both vote callers reach the audience off-tick (through {@code Scheduler.async}), so the adapter marshals the
 * {@code Bukkit.getOnlinePlayers()} read onto the global thread; when the caller already owns the global thread
 * the read runs inline. The recording scheduler asserts which path each call took.
 */
class BukkitVoteAudienceTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        server.addPlayer("Alice");
        server.addPlayer("Bob");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void offGlobalCallMarshalsTheRosterThroughOnGlobal() {
        RecordingScheduler scheduler = new RecordingScheduler(false);
        VoteAudience audience = new BukkitVoteAudience(scheduler);

        Collection<PlayerRef> online = audience.online();

        assertThat(scheduler.globalHops).isEqualTo(1); // the read was marshalled onto the global thread
        assertThat(online).extracting(PlayerRef::name).containsExactlyInAnyOrder("Alice", "Bob");
    }

    @Test
    void onGlobalThreadReadsInlineWithoutHopping() {
        RecordingScheduler scheduler = new RecordingScheduler(true);
        VoteAudience audience = new BukkitVoteAudience(scheduler);

        Collection<PlayerRef> online = audience.online();

        assertThat(scheduler.globalHops).isZero(); // already on the global thread, so no marshal
        assertThat(online).extracting(PlayerRef::name).containsExactlyInAnyOrder("Alice", "Bob");
    }

    /** Runs {@code onGlobal} inline (collapsing the tick boundary) and reports a configurable global-ownership answer. */
    private static final class RecordingScheduler implements Scheduler {

        private final boolean onGlobal;
        int globalHops;

        RecordingScheduler(boolean onGlobal) {
            this.onGlobal = onGlobal;
        }

        @Override
        public boolean onGlobalThread() {
            return onGlobal;
        }

        @Override
        public void onGlobal(Runnable task) {
            globalHops++;
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
