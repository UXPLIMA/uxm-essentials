package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An event about a player is delivered whether or not the player is online.
 *
 * <p>It was not. An event followed the player's own entity, and the scheduler drops a task for an entity that is not
 * there, so money paid to an offline player, a warning given to one and mail sent to one fired no event at all: a
 * ledger plugin missed them and the REST event stream stayed silent.
 */
class RegionTest {

    private static final PlayerRef BOB = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000b0"), "Bob");

    @Test
    @DisplayName("an event about an online player runs on that player's thread")
    void anOnlinePlayerKeepsTheirThread() {
        Recording scheduler = new Recording(true);
        List<String> ran = new ArrayList<>();

        Region.entity(BOB).schedule(scheduler, () -> ran.add("event"));

        assertThat(scheduler.where).containsExactly("entity");
        assertThat(ran).containsExactly("event");
    }

    @Test
    @DisplayName("an event about an offline player runs on the global region rather than nowhere")
    void anOfflinePlayerFallsBackToTheGlobalRegion() {
        Recording scheduler = new Recording(false);
        List<String> ran = new ArrayList<>();

        Region.entity(BOB).schedule(scheduler, () -> ran.add("event"));

        assertThat(scheduler.where).containsExactly("entity", "global");
        assertThat(ran).containsExactly("event");
    }

    /** A scheduler that runs everything inline, and retires an entity task when the player is not there. */
    private static final class Recording implements Scheduler {
        private final boolean online;
        private final List<String> where = new ArrayList<>();

        Recording(boolean online) {
            this.online = online;
        }

        @Override
        public void onGlobal(Runnable task) {
            where.add("global");
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            where.add("region");
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            where.add("entity");
            if (online) {
                task.run();
            }
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            where.add("entity");
            if (online) {
                task.run();
            } else {
                retired.run();
            }
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
