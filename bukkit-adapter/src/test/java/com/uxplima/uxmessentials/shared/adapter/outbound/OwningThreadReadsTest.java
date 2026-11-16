package com.uxplima.uxmessentials.shared.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * A question is answered on the thread that owns what it reads. An async or global caller waits for that answer;
 * another region's thread never waits; and a read that cannot be made gives the caller's safe answer, which for a
 * combat tag is "tagged", not the "not tagged" of a player who left.
 */
class OwningThreadReadsTest {

    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final Position SPAWN = Position.of(new WorldRef(UUID.randomUUID(), "world"), 0, 64, 0);
    private static final String OWNER = "owner";
    private static final Duration BOUND = Duration.ofSeconds(2);

    private final ExecutorService owner = Executors.newSingleThreadExecutor(task -> new Thread(task, OWNER));
    private final OwnerScheduler scheduler = new OwnerScheduler(owner);
    private final FakeThreads threads = new FakeThreads();
    private final Warnings log = new Warnings();
    private long nanos;

    @AfterEach
    void stopOwner() {
        owner.shutdownNow();
    }

    @Test
    void anAsyncCallerGetsTheAnswerReadOnThePlayersThread() {
        AtomicReference<String> readOn = new AtomicReference<>();

        boolean answer = reads(BOUND)
                .forPlayer(
                        ALEX,
                        () -> {
                            readOn.set(Thread.currentThread().getName());
                            return true;
                        },
                        false,
                        false);

        assertThat(answer).isTrue();
        assertThat(readOn).hasValue(OWNER);
    }

    @Test
    void anAsyncCallerGetsTheAnswerReadOnTheRegionThreadOfThePosition() {
        String readOn = reads(BOUND).at(SPAWN, () -> Thread.currentThread().getName(), "gone", "unanswered");

        assertThat(readOn).isEqualTo(OWNER);
        assertThat(scheduler.regions).containsExactly(SPAWN);
    }

    @Test
    void theOwningThreadReadsInPlaceWithoutHopping() {
        threads.tick = true;
        threads.owns = true;

        String readOn =
                reads(BOUND).forPlayer(ALEX, () -> Thread.currentThread().getName(), "gone", "unanswered");

        assertThat(readOn).isEqualTo(Thread.currentThread().getName());
        assertThat(scheduler.hops).isZero();
    }

    @Test
    void anotherRegionsThreadNeitherReadsInPlaceNorWaitsButAnswersUnanswered() {
        threads.tick = true;

        String answer = reads(BOUND).at(SPAWN, () -> "read", "gone", "unanswered");

        assertThat(answer).isEqualTo("unanswered");
        assertThat(scheduler.hops).isZero();
    }

    @Test
    void theGlobalThreadWaitsForTheOwnersAnswer() {
        threads.tick = true;
        threads.global = true;

        String readOn =
                reads(BOUND).forPlayer(ALEX, () -> Thread.currentThread().getName(), "gone", "unanswered");

        assertThat(readOn).isEqualTo(OWNER);
    }

    @Test
    void aPlayerWhoLeftAnswersGone() {
        scheduler.gone = true;

        assertThat(reads(BOUND).forPlayer(ALEX, () -> true, false, true)).isFalse();
    }

    @Test
    void aTagThatCannotBeReadInTimeCountsAsATag() {
        scheduler.stalled = true;

        assertThat(reads(Duration.ofMillis(50)).forPlayer(ALEX, () -> false, false, true))
                .isTrue();
    }

    @Test
    void aStoppingPluginAnswersUnansweredAtOnceWithoutHopping() {
        threads.stopping = true;

        assertThat(reads(BOUND).forPlayer(ALEX, () -> false, false, true)).isTrue();
        assertThat(scheduler.hops).isZero();
    }

    @Test
    void theConsoleHasNoEntityAndAnswersGoneWithoutHopping() {
        PlayerRef console = PlayerRef.system("console");

        assertThat(reads(BOUND).forPlayer(console, () -> true, false, true)).isFalse();
        assertThat(scheduler.hops).isZero();
    }

    @Test
    void aReadThatFailsOnTheOwnerFailsTheCallerAsIfItHadReadInPlace() {
        assertThatThrownBy(() -> reads(BOUND)
                        .forPlayer(
                                ALEX,
                                () -> {
                                    throw new IllegalStateException("no such node");
                                },
                                false,
                                false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("no such node");
    }

    @Test
    void stallsAreReportedAtMostOncePerIntervalWithHowManyThereWere() {
        scheduler.stalled = true;
        OwningThreadReads reads = reads(Duration.ofMillis(10));

        reads.forPlayer(ALEX, () -> true, false, false);
        reads.forPlayer(ALEX, () -> true, false, false);
        reads.forPlayer(ALEX, () -> true, false, false);
        assertThat(log.warnings).containsExactly("stalls=1");

        nanos += OwningThreadReads.STALL_LOG_INTERVAL.toNanos();
        reads.forPlayer(ALEX, () -> true, false, false);
        assertThat(log.warnings).containsExactly("stalls=1", "stalls=3");
    }

    private OwningThreadReads reads(Duration bound) {
        return new OwningThreadReads(scheduler, threads, bound, log, () -> nanos);
    }

    private static final class FakeThreads implements OwningThreadReads.Threads {

        boolean tick;
        boolean global;
        boolean owns;
        boolean stopping;

        @Override
        public boolean onTickThread() {
            return tick;
        }

        @Override
        public boolean onGlobalThread() {
            return global;
        }

        @Override
        public boolean ownsPlayer(PlayerRef player) {
            return owns;
        }

        @Override
        public boolean ownsPosition(Position at) {
            return owns;
        }

        @Override
        public boolean stopping() {
            return stopping;
        }
    }

    /** Runs entity and region work on one owner thread; can lose the player or stall the way a server can. */
    private static final class OwnerScheduler implements Scheduler {

        private final ExecutorService owner;
        final List<Position> regions = new ArrayList<>();
        int hops;
        boolean gone;
        boolean stalled;

        OwnerScheduler(ExecutorService owner) {
            this.owner = owner;
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task, Runnable retired) {
            hops++;
            if (gone) {
                retired.run();
            } else if (!stalled) {
                owner.execute(task);
            }
        }

        @Override
        public void onRegion(Position position, Runnable task, Runnable retired) {
            hops++;
            regions.add(position);
            owner.execute(task);
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void async(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException();
        }
    }

    /** Keeps the stall count each warning carries. */
    private static final class Warnings implements Logger {

        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add("stalls=" + args[1]);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
