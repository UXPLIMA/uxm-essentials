package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/** A periodic sweep keeps running after a pass that throws, and says so in the log. */
class AbstractPeriodicSweepTest {

    @Test
    void aPassThatThrowsIsLoggedAndTheNextPassStillRuns() {
        Steps scheduler = new Steps();
        Errors log = new Errors();
        Flaky sweep = new Flaky(scheduler, log);

        sweep.start();
        scheduler.runNext();
        scheduler.runNext();

        assertThat(sweep.passes).isEqualTo(2);
        assertThat(log.errors).containsExactly("event=playerwarp_sweep_failed sweep=Flaky");
        assertThat(scheduler.delayed).hasSize(1);
    }

    /** Fails on its first pass and succeeds after that. */
    private static final class Flaky extends AbstractPeriodicSweep {

        int passes;

        Flaky(Scheduler scheduler, Logger log) {
            super(scheduler, Duration.ofMinutes(1), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), log);
        }

        @Override
        protected boolean enabled() {
            return true;
        }

        @Override
        public void sweepOnce() {
            passes++;
            if (passes == 1) {
                throw new IllegalStateException("database unavailable");
            }
        }
    }

    /** Runs async work at once and keeps delayed work until the test releases it. */
    private static final class Steps implements Scheduler {

        final Deque<Runnable> delayed = new ArrayDeque<>();

        void runNext() {
            delayed.removeFirst().run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delayed.addLast(task);
        }

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
    }

    private static final class Errors implements Logger {

        final List<String> errors = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            errors.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
