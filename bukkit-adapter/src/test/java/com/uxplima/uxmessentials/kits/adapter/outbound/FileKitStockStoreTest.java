package com.uxplima.uxmessentials.kits.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;

import com.uxplima.uxmessentials.kits.application.port.KitStockStore;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileKitStockStoreTest {

    private static final KitId LIMITED = KitId.of("limited");

    @Test
    void reservesUpToTheLimitThenRefuses(@TempDir Path dir) {
        KitStockStore store = store(dir);

        assertThat(store.tryConsume(LIMITED, 2)).isTrue();
        assertThat(store.tryConsume(LIMITED, 2)).isTrue();
        assertThat(store.tryConsume(LIMITED, 2)).isFalse();
    }

    @Test
    void releaseGivesBackAReservedUnit(@TempDir Path dir) {
        KitStockStore store = store(dir);
        store.tryConsume(LIMITED, 1);
        assertThat(store.tryConsume(LIMITED, 1)).isFalse();

        store.release(LIMITED);

        assertThat(store.tryConsume(LIMITED, 1)).isTrue();
    }

    @Test
    void theCountSurvivesAReload(@TempDir Path dir) {
        Path file = dir.resolve("stock.properties");
        KitStockStore first = new FileKitStockStore(new InlineScheduler(), new NoopLogger(), file);
        first.tryConsume(LIMITED, 5);
        first.tryConsume(LIMITED, 5);

        KitStockStore reloaded = new FileKitStockStore(new InlineScheduler(), new NoopLogger(), file);

        // two of the five are already spent, so only three more reservations succeed
        assertThat(reloaded.tryConsume(LIMITED, 5)).isTrue();
        assertThat(reloaded.tryConsume(LIMITED, 5)).isTrue();
        assertThat(reloaded.tryConsume(LIMITED, 5)).isTrue();
        assertThat(reloaded.tryConsume(LIMITED, 5)).isFalse();
    }

    private static KitStockStore store(Path dir) {
        return new FileKitStockStore(new InlineScheduler(), new NoopLogger(), dir.resolve("stock.properties"));
    }

    /** Runs async work inline so a flush completes synchronously before the test inspects the persisted count. */
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

    private static final class NoopLogger implements Logger {
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
