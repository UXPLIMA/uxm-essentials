package com.uxplima.uxmessentials.messaging.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Pins the fault-tolerance of the self-rescheduling {@link MailExpirySweep}: a delete that throws (a locked db, a
 * transient jOOQ fault) must not kill the sweep. The sweep re-arms from inside its own body, so before the guard a
 * throwing delete would skip the reschedule and mailboxes would grow forever until a module reload. A capturing
 * scheduler fires each sweep by hand.
 */
class MailExpirySweepTest {

    private static final Duration INTERVAL = Duration.ofHours(6);
    private static final Duration RETENTION = Duration.ofDays(30);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void aSweepWhoseDeleteThrowsStillReschedulesAndLogsOneError() {
        CapturingScheduler scheduler = new CapturingScheduler();
        CountingLogger log = new CountingLogger();
        MailRepository mail = mock(MailRepository.class);
        when(mail.deleteSentBefore(any())).thenThrow(new IllegalStateException("db locked"));
        MailExpirySweep sweep = new MailExpirySweep(scheduler, mail, INTERVAL, RETENTION, () -> true, log, CLOCK);

        sweep.start();
        scheduler.runPending();

        assertThat(scheduler.pending)
                .as("the next sweep is still armed after a throwing delete")
                .isNotNull();
        assertThat(scheduler.lastDelay).isEqualTo(INTERVAL);
        assertThat(log.errors).as("the failure is logged exactly once").isEqualTo(1);
    }

    @Test
    void aHealthySweepStillReschedulesWithoutLoggingAnError() {
        CapturingScheduler scheduler = new CapturingScheduler();
        CountingLogger log = new CountingLogger();
        MailRepository mail = mock(MailRepository.class);
        when(mail.deleteSentBefore(any())).thenReturn(0);
        MailExpirySweep sweep = new MailExpirySweep(scheduler, mail, INTERVAL, RETENTION, () -> true, log, CLOCK);

        sweep.start();
        scheduler.runPending();

        assertThat(scheduler.pending).as("a clean sweep re-arms as before").isNotNull();
        assertThat(log.errors).as("a clean sweep logs no error").isZero();
    }

    /** Captures the single pending {@code asyncAfter} task so the test drives each sweep by hand. */
    private static final class CapturingScheduler implements Scheduler {
        private @Nullable Runnable pending;
        private @Nullable Duration lastDelay;

        void runPending() {
            Runnable task = pending;
            pending = null;
            if (task != null) {
                task.run();
            }
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            this.lastDelay = delay;
            this.pending = task;
        }

        @Override
        public void async(Runnable task) {}

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}
    }

    /** A logger that counts the error lines the guard emits. */
    private static final class CountingLogger implements Logger {
        private int errors;

        @Override
        public void error(String message, Throwable cause) {
            errors++;
        }

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
