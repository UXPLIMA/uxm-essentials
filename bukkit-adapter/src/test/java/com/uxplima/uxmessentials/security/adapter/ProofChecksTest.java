package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.VerifyResult;
import com.uxplima.uxmessentials.security.application.port.AttemptStore;
import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import org.junit.jupiter.api.Test;

/**
 * A submitted proof is checked against the shared budget one at a time per player, its attempt counted before it is
 * checked: a burst of submissions on two backends at once checks no more proofs than the budget, and a right proof
 * checked while the account was locked out meanwhile reads as the lockout, never as a pass.
 */
class ProofChecksTest {

    private static final UUID ADA = new UUID(0L, 7L);
    private static final String RIGHT = "4821";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC);

    private final SharedAttempts store = new SharedAttempts();
    private final AtomicInteger checked = new AtomicInteger();

    @Test
    void aPlayerHasOneCheckInFlightAtATime() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ProofChecks checks = new ProofChecks(
                (player, candidate, now) -> {
                    inside.countDown();
                    await(finish);
                    return VerifyResult.INVALID;
                },
                limiter(),
                CLOCK);
        ExecutorService lane = Executors.newSingleThreadExecutor();
        try {
            assertThat(checks.claim(ADA)).isTrue();
            Future<ProofChecks.Checked> first = lane.submit(() -> checks.check(ADA, "0000"));
            assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(checks.claim(ADA))
                    .as("a second submission while the first is checked")
                    .isFalse();
            finish.countDown();
            first.get(10, TimeUnit.SECONDS);

            assertThat(checks.claim(ADA)).as("once the first is done").isTrue();
        } finally {
            lane.shutdownNow();
        }
    }

    @Test
    void aBurstOfWrongProofsOnTwoBackendsChecksNoMoreThanTheBudget() throws Exception {
        ProofChecks survival = counting(limiter());
        ProofChecks lobby = counting(limiter());
        int racers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> done = new ArrayList<>();
        try {
            for (int i = 0; i < racers; i++) {
                ProofChecks backend = i % 2 == 0 ? survival : lobby;
                done.add(pool.submit(() -> {
                    await(start);
                    if (backend.claim(ADA)) {
                        backend.check(ADA, "0000");
                    }
                }));
            }
            start.countDown();
            for (Future<?> each : done) {
                each.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // Whatever the burst left of the budget, spend it one proof at a time.
        for (int i = 0; i < 3; i++) {
            survival.check(ADA, "0000");
        }

        assertThat(checked.get()).isLessThanOrEqualTo(3);
        assertThat(lobby.check(ADA, RIGHT)).as("the budget is spent").isEqualTo(ProofChecks.Checked.LOCKED_OUT);
        assertThat(checked.get()).as("a locked-out account has nothing checked").isLessThanOrEqualTo(3);
    }

    @Test
    void aRightProofCheckedWhileAnotherBackendLockedTheAccountIsTheLockout() {
        AttemptLimiter lobby = limiter();
        ProofChecks survival = new ProofChecks(
                (player, candidate, now) -> {
                    for (int i = 0; i < 3; i++) {
                        lobby.failed(ADA, lobby.begin(ADA, now), now);
                    }
                    return VerifyResult.SUCCESS;
                },
                limiter(),
                CLOCK);

        assertThat(survival.check(ADA, RIGHT)).isEqualTo(ProofChecks.Checked.LOCKED_OUT);
    }

    @Test
    void aRightProofPassesAndStartsTheBudgetAgain() {
        ProofChecks survival = counting(limiter());
        survival.check(ADA, "0000");
        survival.check(ADA, "0000");

        assertThat(survival.check(ADA, RIGHT).result()).isEqualTo(VerifyResult.SUCCESS);
        assertThat(survival.check(ADA, "0000").outcome()).isEqualTo(new AttemptLimiter.Outcome(false, 2));
    }

    private ProofChecks counting(AttemptLimiter limiter) {
        return new ProofChecks(
                (player, candidate, now) -> {
                    checked.incrementAndGet();
                    return candidate.equals(RIGHT) ? VerifyResult.SUCCESS : VerifyResult.INVALID;
                },
                limiter,
                CLOCK);
    }

    private AttemptLimiter limiter() {
        return new AttemptLimiter(store, new NoSessions(), new LockoutPolicy(3), Duration.ofMinutes(5));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    /** The attempt record every backend shares, in memory. */
    private static final class SharedAttempts implements AttemptStore {

        private final Map<UUID, Integer> failures = new HashMap<>();
        private final Map<UUID, Instant> locks = new HashMap<>();

        @Override
        public synchronized Optional<Instant> lockedUntil(UUID player) {
            return Optional.ofNullable(locks.get(player));
        }

        @Override
        public synchronized int addFailure(UUID player) {
            return failures.merge(player, 1, Integer::sum);
        }

        @Override
        public synchronized void lock(UUID player, Instant until) {
            locks.put(player, until);
            failures.put(player, 0);
        }

        @Override
        public synchronized void clearFailures(UUID player) {
            failures.put(player, 0);
        }

        @Override
        public synchronized void clearExpired(UUID player, Instant now) {
            Instant until = locks.get(player);
            if (until != null && !now.isBefore(until)) {
                locks.remove(player);
                failures.remove(player);
            }
        }

        @Override
        public synchronized boolean clear(UUID player, Instant now) {
            failures.remove(player);
            Instant until = locks.remove(player);
            return until != null && now.isBefore(until);
        }
    }

    /** No player here holds a session to carry; a lockout drops none. */
    private static final class NoSessions implements VerifiedSessions {

        @Override
        public Optional<Session> find(UUID player) {
            return Optional.empty();
        }

        @Override
        public void record(
                UUID player, String server, Optional<UUID> connection, Optional<String> ipHash, Instant now) {}

        @Override
        public boolean take(UUID player, String server, Session seen, Instant now) {
            return false;
        }

        @Override
        public void release(UUID player, String server, Instant expiresAt) {}

        @Override
        public void releaseAll(String server, Instant startedAt) {}

        @Override
        public void revoke(UUID player) {}

        @Override
        public boolean proxySeen() {
            return false;
        }

        @Override
        public void markProxySeen(Instant at) {}
    }
}
