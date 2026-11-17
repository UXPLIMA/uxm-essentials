package com.uxplima.uxmessentials.persistence.vote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Queued vote rewards drain in the order they were queued, and once: a second server draining the same player gets
 * nothing, and a reward queued after a drain is drained by the next one.
 */
@EveryBackend
class JooqVoteQueueTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final Instant AT = Instant.parse("2030-04-01T12:00:00Z");

    private DSLContext dsl;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
    }

    @Test
    void queuedRewardsDrainInOrderAndOnlyOnceAcrossServers() {
        VoteRepository lobby = new JooqVoteRepository(dsl);
        VoteRepository survival = new JooqVoteRepository(dsl);
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
        VoteRepository store = new JooqVoteRepository(dsl);
        store.enqueue(new QueuedReward(ALEX, List.of("give {player} diamond"), AT));
        store.drainFor(ALEX);

        store.enqueue(new QueuedReward(ALEX, List.of("give {player} emerald"), AT.plusSeconds(5)));

        assertThat(store.drainFor(ALEX))
                .singleElement()
                .satisfies(batch -> assertThat(batch.commands()).containsExactly("give {player} emerald"));
    }
}
