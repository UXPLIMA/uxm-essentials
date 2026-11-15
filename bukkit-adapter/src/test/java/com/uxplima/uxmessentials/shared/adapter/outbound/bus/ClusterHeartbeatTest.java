package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.junit.jupiter.api.Test;

/** Each heartbeat carries this boot's nonce, which is what lets a peer under the same name be told apart. */
class ClusterHeartbeatTest {

    @Test
    void aHeartbeatCarriesTheBootNonceItWasBuiltWith() {
        List<NetworkMessage> published = new ArrayList<>();
        BusPublisher publisher = new BusPublisher() {
            @Override
            public void publish(NetworkMessage message) {
                published.add(message);
            }

            @Override
            public String serverId() {
                return "survival-1";
            }
        };
        ManualScheduler scheduler = new ManualScheduler();
        ClusterHeartbeat heartbeat = new ClusterHeartbeat(
                publisher, scheduler, Duration.ofSeconds(30), 0x5eedL, new QuietLogger(), () -> 1_000L);

        heartbeat.start();
        scheduler.runNext();

        assertThat(published).containsExactly(new ServerPing("survival-1", 1_000L, 0x5eedL));
    }

    private static final class ManualScheduler implements Scheduler {
        private final Deque<Runnable> delayed = new ArrayDeque<>();

        void runNext() {
            delayed.removeFirst().run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delayed.addLast(task);
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new UnsupportedOperationException("not used by the heartbeat");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException("not used by the heartbeat");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException("not used by the heartbeat");
        }

        @Override
        public void async(Runnable task) {
            throw new UnsupportedOperationException("not used by the heartbeat");
        }
    }

    private static final class QuietLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
