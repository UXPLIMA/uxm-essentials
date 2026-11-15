package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.adapter.outbound.config.ConfigurateConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The proxy drops every frame sent to an empty backend, so the caches peers change reload when the backend fills
 * again and on the network.cache-refresh-seconds timer, and never on a backend that is off the network.
 */
class SharedCacheRefreshTest {

    private static final Logger QUIET = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    @TempDir
    Path folder;

    private final ManualScheduler scheduler = new ManualScheduler();
    private final RemoteSyncRegistry registry = new RemoteSyncRegistry();
    private final AtomicInteger reloads = new AtomicInteger();
    private final AtomicInteger playerDrops = new AtomicInteger();

    private SharedCacheRefresh refresh(String network, int onlineNow) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "network {\n" + network + "\n}\n");
        NetworkConfig config = NetworkConfig.from(ConfigurateConfigStore.load(file, QUIET));
        registry.registerResync(reloads::incrementAndGet);
        registry.registerPlayerCacheDrop(playerDrops::incrementAndGet);
        return SharedCacheRefresh.wire(config, registry, scheduler, QUIET, onlineNow);
    }

    @Test
    void theFirstPlayerOnAnEmptyBackendReloadsTheSharedCaches() throws IOException {
        SharedCacheRefresh refresh = refresh("enabled = true", 0);

        refresh.playerJoined();
        // The per-player caches drop on the joining thread, before the join handlers warm the joiner.
        assertThat(playerDrops).hasValue(1);
        scheduler.runAsync();

        assertThat(reloads).hasValue(1);
    }

    @Test
    void aSecondPlayerJoiningReloadsNothing() throws IOException {
        SharedCacheRefresh refresh = refresh("enabled = true", 1);

        refresh.playerJoined();
        scheduler.runAsync();

        assertThat(reloads).hasValue(0);
        assertThat(playerDrops).hasValue(0);
    }

    @Test
    void aBackendThatEmptiedReloadsOnTheNextJoin() throws IOException {
        SharedCacheRefresh refresh = refresh("enabled = true", 2);

        // Two quits that each ran on their own region thread still empty the backend.
        refresh.playerLeft();
        refresh.playerLeft();
        refresh.playerJoined();
        refresh.playerJoined();
        scheduler.runAsync();

        assertThat(reloads).hasValue(1);
        assertThat(playerDrops).hasValue(1);
    }

    @Test
    void aBackendOffTheNetworkNeverReloads() throws IOException {
        SharedCacheRefresh refresh = refresh("enabled = false\n  cache-refresh-seconds = 60", 0);

        refresh.start();
        refresh.playerJoined();
        scheduler.runAsync();

        assertThat(reloads).hasValue(0);
        assertThat(scheduler.delays).isEmpty();
    }

    @Test
    void aRedisOnlyNetworkDoesNotReloadOnJoinBecauseRedisReachesAnEmptyBackend() throws IOException {
        SharedCacheRefresh refresh = refresh("enabled = true\n  transport = \"redis\"", 0);

        refresh.playerJoined();
        scheduler.runAsync();

        assertThat(reloads).hasValue(0);
    }

    @Test
    void theTimerReloadsEveryConfiguredPeriod() throws IOException {
        SharedCacheRefresh refresh = refresh("enabled = true\n  cache-refresh-seconds = 60", 1);

        refresh.start();
        scheduler.runNextDelayed();
        scheduler.runNextDelayed();

        assertThat(reloads).hasValue(2);
        assertThat(scheduler.delays).containsOnly(Duration.ofSeconds(60));
        assertThat(playerDrops).hasValue(0);
    }

    @Test
    void aJoinAndATimerTickTogetherReloadOnce() throws IOException {
        SharedCacheRefresh refresh = refresh("enabled = true\n  cache-refresh-seconds = 60", 0);

        refresh.start();
        refresh.playerJoined();
        scheduler.runNextDelayed();
        scheduler.runAsync();

        assertThat(reloads).hasValue(1);
    }

    @Test
    void zeroSecondsTurnsTheTimerOff() throws IOException {
        SharedCacheRefresh refresh = refresh("enabled = true\n  cache-refresh-seconds = 0", 1);

        refresh.start();

        assertThat(scheduler.delays).isEmpty();
    }

    @Test
    void aStoppedTimerReloadsNoMore() throws IOException {
        SharedCacheRefresh refresh = refresh("enabled = true\n  cache-refresh-seconds = 60", 1);

        refresh.start();
        refresh.stop();
        scheduler.runNextDelayed();

        assertThat(reloads).hasValue(0);
    }

    @Test
    void aResyncThatThrowsDoesNotStopTheOthersOrTheTimer() throws IOException {
        registry.registerResync(() -> {
            throw new IllegalStateException("database down");
        });
        SharedCacheRefresh refresh = refresh("enabled = true\n  cache-refresh-seconds = 60", 1);

        refresh.start();
        scheduler.runNextDelayed();
        scheduler.runNextDelayed();

        assertThat(reloads).hasValue(2);
    }

    private static final class ManualScheduler implements Scheduler {
        private final Deque<Runnable> async = new ArrayDeque<>();
        private final Deque<Runnable> delayed = new ArrayDeque<>();
        private final List<Duration> delays = new ArrayList<>();

        void runAsync() {
            while (!async.isEmpty()) {
                async.removeFirst().run();
            }
        }

        void runNextDelayed() {
            if (!delayed.isEmpty()) {
                delayed.removeFirst().run();
            }
            runAsync();
        }

        @Override
        public void async(Runnable task) {
            async.addLast(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delays.add(delay);
            delayed.addLast(task);
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new UnsupportedOperationException("the refresh never touches the global region");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException("the refresh never touches a region");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException("the refresh never touches an entity");
        }
    }
}
