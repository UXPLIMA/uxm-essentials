package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpShuffle;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.Test;

/** The random browse order is dealt again at every round boundary, the same round on every server. */
class RandomOrderSweepTest {

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Instant TEN_TWENTY = Instant.parse("2030-01-01T10:20:00Z");
    private static final long TEN_O_CLOCK_ROUND =
            Instant.parse("2030-01-01T10:00:00Z").toEpochMilli() / 3_600_000L;

    private final Steps scheduler = new Steps();
    private final Dealt dealt = new Dealt();
    private final MovableClock clock = new MovableClock(TEN_TWENTY);

    private RandomOrderSweep sweep(Duration round) {
        return new RandomOrderSweep(dealt, scheduler, round, clock, new Quiet());
    }

    @Test
    void startDealsTheCurrentRoundAndWaitsForTheNextBoundary() {
        sweep(HOUR).start();

        assertThat(dealt.rounds).containsExactly(TEN_O_CLOCK_ROUND);
        assertThat(scheduler.delays).containsExactly(Duration.ofMinutes(40));
    }

    @Test
    void eachBoundaryDealsTheNextRound() {
        sweep(HOUR).start();
        clock.now = Instant.parse("2030-01-01T11:00:00Z");

        scheduler.runNext();

        assertThat(dealt.rounds).containsExactly(TEN_O_CLOCK_ROUND, TEN_O_CLOCK_ROUND + 1);
        assertThat(scheduler.delays).containsExactly(Duration.ofMinutes(40), HOUR);
    }

    @Test
    void serversStartedAtDifferentTimesInOneRoundDealTheSameRound() {
        clock.now = Instant.parse("2030-01-01T10:05:00Z");
        sweep(HOUR).start();
        clock.now = Instant.parse("2030-01-01T10:55:00Z");
        sweep(HOUR).start();

        assertThat(dealt.rounds).containsExactly(TEN_O_CLOCK_ROUND, TEN_O_CLOCK_ROUND);
        assertThat(scheduler.delays).containsExactly(Duration.ofMinutes(55), Duration.ofMinutes(5));
    }

    @Test
    void aRoundOfZeroNeverDeals() {
        sweep(Duration.ZERO).start();

        assertThat(dealt.rounds).isEmpty();
        assertThat(scheduler.delayed).isEmpty();
    }

    @Test
    void afterStopNoRoundIsDealt() {
        RandomOrderSweep sweep = sweep(HOUR);
        sweep.start();
        sweep.stop();
        clock.now = Instant.parse("2030-01-01T11:00:00Z");

        scheduler.runNext();

        assertThat(dealt.rounds).containsExactly(TEN_O_CLOCK_ROUND);
        assertThat(scheduler.delayed).isEmpty();
    }

    /** Records the rounds it is asked to deal. */
    private static final class Dealt implements PlayerWarpShuffle {

        final List<Long> rounds = new ArrayList<>();

        @Override
        public int shuffle(long round) {
            rounds.add(round);
            return 0;
        }
    }

    /** A clock the test moves by hand. */
    private static final class MovableClock extends Clock {

        Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Runs async work at once and keeps delayed work, with its delay, until the test releases it. */
    private static final class Steps implements Scheduler {

        final Deque<Runnable> delayed = new ArrayDeque<>();
        final List<Duration> delays = new ArrayList<>();

        void runNext() {
            delayed.removeFirst().run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            delays.add(delay);
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

    private static final class Quiet implements Logger {

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
