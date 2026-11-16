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
 * A question asked off the tick is answered on the thread that owns what it reads, and the asker waits for the
 * answer; one asked on a tick thread is answered in place.
 */
class OwningThreadReadsTest {

    private static final PlayerRef ALEX = new PlayerRef(UUID.randomUUID(), "Alex");
    private static final Position SPAWN = Position.of(new WorldRef(UUID.randomUUID(), "world"), 0, 64, 0);
    private static final String OWNER = "owner";

    private final ExecutorService owner = Executors.newSingleThreadExecutor(task -> new Thread(task, OWNER));
    private final OwnerScheduler scheduler = new OwnerScheduler(owner);
    private final Warnings log = new Warnings();

    @AfterEach
    void stopOwner() {
        owner.shutdownNow();
    }

    @Test
    void anAsyncCallerGetsTheAnswerReadOnThePlayersThread() {
        OwningThreadReads reads = offTick(Duration.ofSeconds(2));
        AtomicReference<String> readOn = new AtomicReference<>();

        boolean answer = reads.forPlayer(
                ALEX,
                () -> {
                    readOn.set(Thread.currentThread().getName());
                    return true;
                },
                false);

        assertThat(answer).isTrue();
        assertThat(readOn).hasValue(OWNER);
    }

    @Test
    void anAsyncCallerGetsTheAnswerReadOnTheRegionThreadOfThePosition() {
        OwningThreadReads reads = offTick(Duration.ofSeconds(2));

        String readOn = reads.at(SPAWN, () -> Thread.currentThread().getName(), "nowhere");

        assertThat(readOn).isEqualTo(OWNER);
        assertThat(scheduler.regions).containsExactly(SPAWN);
    }

    @Test
    void aCallerOnATickThreadReadsInPlaceWithoutHopping() {
        OwningThreadReads reads = new OwningThreadReads(scheduler, () -> true, Duration.ofSeconds(2), log);

        String readOn = reads.forPlayer(ALEX, () -> Thread.currentThread().getName(), "nowhere");

        assertThat(readOn).isEqualTo(Thread.currentThread().getName());
        assertThat(scheduler.hops).isZero();
    }

    @Test
    void aPlayerWhoLeftAnswersTheFallback() {
        scheduler.gone = true;
        OwningThreadReads reads = offTick(Duration.ofSeconds(2));

        assertThat(reads.forPlayer(ALEX, () -> true, false)).isFalse();
    }

    @Test
    void anOwnerThatNeverRunsTheReadAnswersTheFallbackOnceTheWaitEndsAndSaysSoOnce() {
        scheduler.stalled = true;
        OwningThreadReads reads = offTick(Duration.ofMillis(50));

        assertThat(reads.forPlayer(ALEX, () -> true, false)).isFalse();
        assertThat(reads.forPlayer(ALEX, () -> true, false)).isFalse();
        assertThat(log.warnings).hasSize(1);
    }

    @Test
    void aReadThatFailsOnTheOwnerFailsTheCallerAsIfItHadReadInPlace() {
        OwningThreadReads reads = offTick(Duration.ofSeconds(2));

        assertThatThrownBy(() -> reads.forPlayer(
                        ALEX,
                        () -> {
                            throw new IllegalStateException("no such node");
                        },
                        false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("no such node");
    }

    private OwningThreadReads offTick(Duration timeout) {
        return new OwningThreadReads(scheduler, () -> false, timeout, log);
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

    private static final class Warnings implements Logger {

        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
