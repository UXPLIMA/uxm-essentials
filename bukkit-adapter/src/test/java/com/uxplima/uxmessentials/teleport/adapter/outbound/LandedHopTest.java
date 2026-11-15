package com.uxplima.uxmessentials.teleport.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/**
 * A hop {@code teleportAsync} completed is a landing whatever happens next: a paid teleport settles its money on it,
 * so a player who leaves in that tick, an arrival effect that throws, or a stopping scheduler must not swallow it.
 */
class LandedHopTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e5"), "Alex");

    private final PlayerThread thread = new PlayerThread();
    private final Errors log = new Errors();
    private final List<String> happened = new ArrayList<>();

    private void report(Runnable arrival) {
        LandedHop.report(thread, log, ALEX, arrival, () -> happened.add("landed"));
    }

    @Test
    void theArrivalRunsOnThePlayersThreadAndThenTheLandingIsReported() {
        report(() -> happened.add("arrival"));
        assertThat(happened).as("nothing runs before the player's thread").isEmpty();

        thread.run();

        assertThat(happened).containsExactly("arrival", "landed");
    }

    @Test
    void aPlayerWhoLeftInThatTickIsStillReportedAsLanded() {
        thread.gone = true;

        report(() -> happened.add("arrival"));

        assertThat(happened).containsExactly("landed");
    }

    @Test
    void anArrivalEffectThatThrowsStillReportsTheLanding() {
        report(() -> {
            throw new IllegalStateException("the title failed");
        });

        thread.run();

        assertThat(happened).containsExactly("landed");
        assertThat(log.errors)
                .singleElement()
                .satisfies(line -> assertThat(line).contains("Alex"));
    }

    @Test
    void aSchedulerThatRefusesTheHopStillReportsTheLanding() {
        thread.refuses = true;

        report(() -> happened.add("arrival"));

        assertThat(happened).containsExactly("landed");
    }

    /** The player's thread; a player who is gone retires the hop, and a stopping scheduler refuses it. */
    private static final class PlayerThread implements Scheduler {

        private final List<Runnable> waiting = new ArrayList<>();
        private boolean gone;
        private boolean refuses;

        void run() {
            List<Runnable> due = List.copyOf(waiting);
            waiting.clear();
            due.forEach(Runnable::run);
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
            onEntity(player, task, () -> {});
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            if (refuses) {
                throw new IllegalStateException("the scheduler is stopping");
            }
            if (gone) {
                retired.run();
            } else {
                waiting.add(task);
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

    /** Keeps the error lines, drops the rest. */
    private static final class Errors implements Logger {

        private final List<String> errors = new ArrayList<>();

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
