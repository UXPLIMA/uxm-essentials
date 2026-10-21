package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * The HUD loop spreads the roster over its interval. It used to queue every online player on the same tick, so a
 * server drew the whole roster's sidebar and tab list in one tick each second: with thirty players on a test lobby
 * that tick took close to thirty milliseconds. Each player now has a fixed slice of the interval, renders once per
 * interval in it, and the animation clock still steps once per interval.
 */
class HudRenderSpreadsPlayersOverTheIntervalTest {

    private static final Duration INTERVAL = Duration.ofSeconds(1);
    private static final int PLAYERS = 40;

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        for (int i = 0; i < PLAYERS; i++) {
            server.addPlayer();
        }
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void everyPlayerRendersOnceAnIntervalAndNoSliceCarriesTheRoster() {
        SliceScheduler scheduler = new SliceScheduler();
        AnimationRegistry animations = mock(AnimationRegistry.class);
        ScoreboardRenderTask task = new ScoreboardRenderTask(
                scheduler, mock(ScoreboardRenderer.class), animations, new QuietLogger(), () -> INTERVAL, () -> true);

        task.start();
        Duration elapsed = Duration.ZERO;
        List<Integer> perSlice = new ArrayList<>();
        while (elapsed.compareTo(INTERVAL) < 0) {
            elapsed = elapsed.plus(scheduler.delay());
            perSlice.add(scheduler.fireNext());
        }

        assertThat(scheduler.renders).hasSize(PLAYERS);
        assertThat(scheduler.renders.values()).containsOnly(1);
        assertThat(perSlice).as("renders per slice").allMatch(count -> count < PLAYERS / 2);
        verify(animations, times(1)).advance();
    }

    /** Fires one pending loop tick by hand and counts the per-player renders it queues. */
    private static final class SliceScheduler implements Scheduler {
        private @Nullable Runnable pending;
        private @Nullable Duration delay;
        private final Map<UUID, Integer> renders = new HashMap<>();
        private int queued;

        Duration delay() {
            return delay == null ? Duration.ZERO : delay;
        }

        int fireNext() {
            Runnable next = pending;
            pending = null;
            queued = 0;
            if (next != null) {
                next.run();
            }
            return queued;
        }

        @Override
        public void asyncAfter(Duration after, Runnable task) {
            this.pending = task;
            this.delay = after;
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            queued++;
            renders.merge(player.uuid(), 1, Integer::sum);
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
