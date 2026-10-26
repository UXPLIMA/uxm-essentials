package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * A render the server drops is not the last render a player gets.
 *
 * <p>The loop keeps a player marked as waiting until their render has run, and queues nobody who is waiting. The
 * server drops a task queued on a player whose entity it retires, and the loop never heard of it: the player stayed
 * marked, and their sidebar, tab list and boss bars stopped on the frame they showed. The owner's tab stood still
 * on the lobby on 2026-10-26 while every bot that joined after him turned.
 */
class HudRenderSurvivesADroppedRenderTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aPlayerWhoseRenderWasDroppedIsDrawnOnTheNextTick() {
        Player player = server.addPlayer();
        DroppingScheduler scheduler = new DroppingScheduler();
        ScoreboardRenderer renderer = mock(ScoreboardRenderer.class);
        ScoreboardRenderTask task = new ScoreboardRenderTask(
                scheduler,
                renderer,
                mock(AnimationRegistry.class),
                new QuietLogger(),
                () -> Duration.ofMillis(50),
                () -> true);

        task.start();
        scheduler.fireNext();
        scheduler.fireNext();

        verify(renderer, times(1)).renderFor(player);
    }

    /**
     * Drops the first task queued on a player, the way the server drops one on a retired entity, and runs every task
     * after it. The retired callback is run when the caller gave one; the plain form drops the task without a word.
     */
    private static final class DroppingScheduler implements Scheduler {
        private @Nullable Runnable pending;
        private boolean dropped;

        void fireNext() {
            Runnable next = pending;
            pending = null;
            if (next != null) {
                next.run();
            }
        }

        @Override
        public void asyncAfter(Duration after, Runnable task) {
            this.pending = task;
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            if (!dropped) {
                dropped = true;
                return;
            }
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            if (!dropped) {
                dropped = true;
                retired.run();
                return;
            }
            task.run();
        }

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void async(Runnable task) {}
    }

    private static final class QuietLogger implements Logger {
        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
