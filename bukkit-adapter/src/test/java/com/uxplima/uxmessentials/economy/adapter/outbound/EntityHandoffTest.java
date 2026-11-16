package com.uxplima.uxmessentials.economy.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

/**
 * A physical-currency credit waits a bounded time for the player's thread. A wait that runs out must never be followed
 * by the credit landing anyway: the caller has already been told it failed and acts on that (a sale gives the items
 * back), so a late credit would pay twice.
 */
class EntityHandoffTest {

    private static final Duration NO_WAIT = Duration.ZERO;

    private final Recorded outcomes = new Recorded();
    private final AtomicInteger credits = new AtomicInteger();

    @Test
    void workTheWaiterGaveUpOnNeverRunsWhenThePlayersThreadGetsToIt() {
        EntityHandoff<String> handoff = new EntityHandoff<>();

        String answer = handoff.await(NO_WAIT, "refused", outcomes);
        handoff.run(this::credit);

        assertThat(answer).isEqualTo("refused");
        assertThat(credits).hasValue(0);
        assertThat(outcomes.events).containsExactly("timed out");
    }

    @Test
    void workThatRanBeforeTheWaitIsAnswered() {
        EntityHandoff<String> handoff = new EntityHandoff<>();

        handoff.run(this::credit);
        String answer = handoff.await(NO_WAIT, "refused", outcomes);

        assertThat(answer).isEqualTo("credited");
        assertThat(credits).hasValue(1);
        assertThat(outcomes.events).isEmpty();
    }

    @Test
    void aPlayerWhoLeftBeforeTheWorkRanGetsTheFallbackAndNothingLater() {
        EntityHandoff<String> handoff = new EntityHandoff<>();

        handoff.retire("refused");
        handoff.run(this::credit);

        assertThat(handoff.await(NO_WAIT, "refused", outcomes)).isEqualTo("refused");
        assertThat(credits).hasValue(0);
    }

    @Test
    void workAlreadyRunningWhenTheWaitRunsOutIsWaitedForAndReported() throws Exception {
        EntityHandoff<String> handoff = new EntityHandoff<>();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread playerThread = new Thread(() -> handoff.run(() -> {
            started.countDown();
            awaitQuietly(release);
            return credit();
        }));
        playerThread.start();
        started.await();

        CompletableFuture<String> answer = new CompletableFuture<>();
        Thread caller = new Thread(() -> answer.complete(handoff.await(NO_WAIT, "refused", outcomes)));
        caller.start();
        release.countDown();

        assertThat(answer.get(10, TimeUnit.SECONDS)).isEqualTo("credited");
        assertThat(credits).hasValue(1);
        assertThat(outcomes.events).isEmpty();
        playerThread.join();
        caller.join();
    }

    @Test
    void workThatThrowsAnswersTheFallbackAndReportsTheFailure() {
        EntityHandoff<String> handoff = new EntityHandoff<>();

        handoff.run(() -> {
            throw new IllegalStateException("inventory is gone");
        });

        assertThat(handoff.await(NO_WAIT, "refused", outcomes)).isEqualTo("refused");
        assertThat(outcomes.events).containsExactly("failed: inventory is gone");
    }

    private String credit() {
        credits.incrementAndGet();
        return "credited";
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Recorded implements EntityHandoff.Outcomes {

        final List<String> events = new ArrayList<>();

        @Override
        public synchronized void timedOut() {
            events.add("timed out");
        }

        @Override
        public synchronized void failed(Throwable failure) {
            events.add("failed: " + failure.getMessage());
        }
    }
}
