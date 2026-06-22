package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link ClusterHeartbeat}'s self-rescheduling loop with a scheduler that captures the deferred task
 * instead of running it, so the test fires each tick explicitly. The properties pinned: starting schedules the
 * first tick after one interval, a fired tick publishes a {@link ServerPing} stamped with this backend's id and
 * the injected clock, each tick reschedules the next, and a stopped heartbeat publishes nothing further.
 */
class ClusterHeartbeatTest {

    private static final String SELF = "survival-1";
    private static final Duration INTERVAL = Duration.ofSeconds(30);

    @Test
    void startSchedulesTheFirstTickAfterOneInterval() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ClusterHeartbeat heartbeat = new ClusterHeartbeat(new CapturingPublisher(SELF), scheduler, INTERVAL, () -> 1L);

        heartbeat.start();

        assertThat(scheduler.lastDelay).isEqualTo(INTERVAL);
        assertThat(scheduler.pending).isNotNull();
    }

    @Test
    void aFiredTickPublishesAServerPingWithThisBackendsIdAndTheClock() {
        RecordingScheduler scheduler = new RecordingScheduler();
        CapturingPublisher publisher = new CapturingPublisher(SELF);
        ClusterHeartbeat heartbeat = new ClusterHeartbeat(publisher, scheduler, INTERVAL, () -> 42L);

        heartbeat.start();
        scheduler.fireNext();

        assertThat(publisher.published).containsExactly(new ServerPing(SELF, 42L));
    }

    @Test
    void eachTickReschedulesTheNext() {
        RecordingScheduler scheduler = new RecordingScheduler();
        ClusterHeartbeat heartbeat = new ClusterHeartbeat(new CapturingPublisher(SELF), scheduler, INTERVAL, () -> 7L);

        heartbeat.start();
        scheduler.fireNext();

        assertThat(scheduler.scheduleCount)
                .as("the initial schedule plus the one the tick added")
                .isEqualTo(2);
    }

    @Test
    void aStoppedHeartbeatPublishesNothingFurther() {
        RecordingScheduler scheduler = new RecordingScheduler();
        CapturingPublisher publisher = new CapturingPublisher(SELF);
        ClusterHeartbeat heartbeat = new ClusterHeartbeat(publisher, scheduler, INTERVAL, () -> 1L);

        heartbeat.start();
        heartbeat.stop();
        // The transport already had a tick queued before stop; firing it must be a no-op now.
        scheduler.fireNext();

        assertThat(publisher.published).isEmpty();
    }

    @Test
    void aHeartbeatThatWasNeverStartedPublishesNothing() {
        RecordingScheduler scheduler = new RecordingScheduler();
        CapturingPublisher publisher = new CapturingPublisher(SELF);
        new ClusterHeartbeat(publisher, scheduler, INTERVAL, () -> 1L);

        assertThat(scheduler.scheduleCount).isZero();
        assertThat(publisher.published).isEmpty();
    }

    /** A scheduler that captures the deferred async task so the test fires ticks one at a time. */
    private static final class RecordingScheduler implements Scheduler {

        private @org.jspecify.annotations.Nullable Runnable pending;
        private @org.jspecify.annotations.Nullable Duration lastDelay;
        private int scheduleCount;

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            this.pending = task;
            this.lastDelay = delay;
            this.scheduleCount++;
        }

        void fireNext() {
            Runnable next = pending;
            pending = null;
            if (next != null) {
                next.run();
            }
        }

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}
    }

    /** A publisher that records every frame instead of moving bytes; stamps the configured self id. */
    private static final class CapturingPublisher implements BusPublisher {

        private final String serverId;
        private final List<NetworkMessage> published = new ArrayList<>();

        CapturingPublisher(String serverId) {
            this.serverId = serverId;
        }

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return serverId;
        }
    }
}
