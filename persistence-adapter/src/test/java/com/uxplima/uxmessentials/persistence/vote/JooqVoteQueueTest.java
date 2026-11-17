package com.uxplima.uxmessentials.persistence.vote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import org.jooq.DSLContext;
import org.jooq.Delete;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Queued vote rewards drain in the order they were queued, and once: a second server draining the same player gets
 * nothing, a reward queued after a drain is drained by the next one, and a drain the database fails part way through
 * still hands back the rewards it already removed.
 */
@EveryBackend
class JooqVoteQueueTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final Instant AT = Instant.parse("2030-04-01T12:00:00Z");

    private final RecordingLogger log = new RecordingLogger();
    private DSLContext dsl;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
    }

    @Test
    void queuedRewardsDrainInOrderAndOnlyOnceAcrossServers() {
        VoteRepository lobby = new JooqVoteRepository(dsl, log);
        VoteRepository survival = new JooqVoteRepository(dsl, log);
        lobby.enqueue(new QueuedReward(ALEX, List.of("give {player} diamond", "eco give {player} 10"), AT));

        List<QueuedReward> first = survival.drainFor(ALEX);
        List<QueuedReward> second = lobby.drainFor(ALEX);

        assertThat(first)
                .singleElement()
                .satisfies(batch ->
                        assertThat(batch.commands()).containsExactly("give {player} diamond", "eco give {player} 10"));
        assertThat(second).isEmpty();
        assertThat(lobby.hasPending(ALEX)).isFalse();
    }

    @Test
    void aRewardQueuedAfterADrainIsDrainedByTheNextOne() {
        VoteRepository store = new JooqVoteRepository(dsl, log);
        store.enqueue(new QueuedReward(ALEX, List.of("give {player} diamond"), AT));
        store.drainFor(ALEX);

        store.enqueue(new QueuedReward(ALEX, List.of("give {player} emerald"), AT.plusSeconds(5)));

        assertThat(store.drainFor(ALEX))
                .singleElement()
                .satisfies(batch -> assertThat(batch.commands()).containsExactly("give {player} emerald"));
    }

    @Test
    void aDrainThatFailsPartWayPaysTheRewardsItAlreadyRemovedAndLeavesTheRest() {
        new JooqVoteRepository(dsl, log)
                .enqueue(new QueuedReward(
                        ALEX, List.of("give {player} diamond", "give {player} emerald", "give {player} gold"), AT));
        VoteRepository failing = new JooqVoteRepository(failingOnDelete(2), log);

        List<QueuedReward> drained = failing.drainFor(ALEX);

        assertThat(drained)
                .as("the first row is gone from the queue, so it must be paid")
                .singleElement()
                .satisfies(batch -> assertThat(batch.commands()).containsExactly("give {player} diamond"));
        assertThat(log.errors).hasSize(1);
        assertThat(new JooqVoteRepository(dsl, log).drainFor(ALEX))
                .as("the rows the failed drain left are paid by the next one")
                .singleElement()
                .satisfies(batch ->
                        assertThat(batch.commands()).containsExactly("give {player} emerald", "give {player} gold"));
    }

    /** The shared database, failing the {@code nth} delete it is asked to run, as a deadlock or a pool timeout does. */
    private DSLContext failingOnDelete(int nth) {
        AtomicInteger deletes = new AtomicInteger();
        ExecuteListener failure = new ExecuteListener() {
            @Override
            public void executeStart(ExecuteContext ctx) {
                if (ctx.query() instanceof Delete<?> && deletes.incrementAndGet() == nth) {
                    throw new DataAccessException("deadlock found when trying to get lock");
                }
            }
        };
        return dsl.configuration().deriveAppending(failure).dsl();
    }

    /** Keeps the error lines, which are what an operator would be told. */
    private static final class RecordingLogger implements Logger {

        private final List<String> errors = new CopyOnWriteArrayList<>();

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
