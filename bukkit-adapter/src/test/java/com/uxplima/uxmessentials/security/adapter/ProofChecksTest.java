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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.security.application.AttemptLimiter;
import com.uxplima.uxmessentials.security.application.VerifyResult;
import com.uxplima.uxmessentials.security.application.port.AttemptStore;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.Test;

/**
 * A submitted proof is checked against the shared budget one at a time per player, its attempt counted before it is
 * checked: a burst of submissions on two backends at once checks no more proofs than the budget, a right proof checked
 * while the account was locked out meanwhile reads as the lockout, and a proof that cannot be checked at all gives its
 * attempt back.
 */
class ProofChecksTest {

    private static final UUID ADA = new UUID(0L, 7L);
    private static final String RIGHT = "4821";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC);

    private final SharedAttempts store = new SharedAttempts();
    private final AtomicInteger checked = new AtomicInteger();
    private final List<String> errors = new CopyOnWriteArrayList<>();

    @Test
    void aSecondSubmissionWhileTheFirstIsCheckedIsNotChecked() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ProofChecks checks = checks(
                (player, candidate, now) -> {
                    inside.countDown();
                    await(finish);
                    return VerifyResult.INVALID;
                },
                limiter());
        ExecutorService lane = Executors.newSingleThreadExecutor();
        try {
            Future<ProofChecks.Checked> first = lane.submit(() -> checks.check(ADA, "0000"));
            assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

            assertThat(checks.check(ADA, "1111")).isEqualTo(ProofChecks.Checked.BUSY);
            finish.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(ProofChecks.Status.WRONG);
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
                    backend.check(ADA, "0000");
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
        ProofChecks survival = checks(
                (player, candidate, now) -> {
                    for (int i = 0; i < 3; i++) {
                        lobby.failed(ADA, lobby.begin(ADA, now), now);
                    }
                    return VerifyResult.SUCCESS;
                },
                limiter());

        assertThat(survival.check(ADA, RIGHT)).isEqualTo(ProofChecks.Checked.LOCKED_OUT);
    }

    @Test
    void aRightProofPassesAndStartsTheBudgetAgain() {
        ProofChecks survival = counting(limiter());
        survival.check(ADA, "0000");
        survival.check(ADA, "0000");

        assertThat(survival.check(ADA, RIGHT).result()).isEqualTo(VerifyResult.SUCCESS);
        assertThat(survival.check(ADA, "0000"))
                .isEqualTo(new ProofChecks.Checked(ProofChecks.Status.WRONG, VerifyResult.INVALID, 2));
    }

    @Test
    void aProofThatCannotBeCheckedGivesItsAttemptBackAndSaysSo() {
        ProofChecks outage = checks(
                (player, candidate, now) -> {
                    throw new IllegalStateException("database down");
                },
                limiter());
        ProofChecks survival = counting(limiter());
        survival.check(ADA, "0000");

        for (int i = 0; i < 5; i++) {
            assertThat(outage.check(ADA, RIGHT)).isEqualTo(ProofChecks.Checked.UNAVAILABLE);
        }

        assertThat(errors).hasSize(5).allMatch(line -> line.startsWith("event=security_proof_unchecked"));
        assertThat(survival.check(ADA, "0000"))
                .as("the outage spent nothing: one wrong proof before it, this is the second")
                .isEqualTo(new ProofChecks.Checked(ProofChecks.Status.WRONG, VerifyResult.INVALID, 1));
    }

    private ProofChecks counting(AttemptLimiter limiter) {
        return checks(
                (player, candidate, now) -> {
                    checked.incrementAndGet();
                    return candidate.equals(RIGHT) ? VerifyResult.SUCCESS : VerifyResult.INVALID;
                },
                limiter);
    }

    private ProofChecks checks(ProofChecks.Prover prover, AttemptLimiter limiter) {
        return new ProofChecks(prover, limiter, new Errors(errors), CLOCK);
    }

    private AttemptLimiter limiter() {
        return new AttemptLimiter(store, player -> {}, new LockoutPolicy(3), Duration.ofMinutes(5));
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
        public synchronized void refundFailure(UUID player) {
            failures.computeIfPresent(player, (id, count) -> Math.max(0, count - 1));
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

    private record Errors(List<String> lines) implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            lines.add(message);
        }

        @Override
        public void debug(String message, Object... args) {}
    }
}
