package com.uxplima.uxmessentials.persistence.vote;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.vote.application.port.ParkedVotes;
import com.uxplima.uxmessentials.vote.domain.ParkedVote;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Parked votes are found by any case of their name, oldest first, except one that needs its exact spelling, and are
 * handed out once: a second server taking after the first gets nothing.
 */
@EveryBackend
class JooqParkedVotesTest {

    private static final Instant FIRST = Instant.parse("2030-03-01T12:00:00Z");
    private static final Instant SECOND = FIRST.plusSeconds(60);

    private DSLContext dsl;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
    }

    @Test
    void aJoinerClaimsTheVotesForAnyCaseOfTheirNameOldestFirst() {
        ParkedVotes store = new JooqParkedVotes(dsl);
        ParkedVote later = new ParkedVote("STEVE", "site-b", SECOND, false);
        ParkedVote earlier = new ParkedVote("steve", "site-a", FIRST, false);
        ParkedVote other = new ParkedVote("Alex", "site-a", FIRST, false);
        store.park(later);
        store.park(earlier);
        store.park(other);

        assertThat(store.take(Set.of("Steve"))).containsExactly(earlier, later);
        assertThat(store.take(Set.of("Alex"))).containsExactly(other);
    }

    @Test
    void aVoteThatNeedsItsExactSpellingStaysForThatSpelling() {
        ParkedVotes store = new JooqParkedVotes(dsl);
        ParkedVote exact = new ParkedVote("steve", "site-a", FIRST, true);
        store.park(exact);

        assertThat(store.take(Set.of("Steve"))).isEmpty();
        assertThat(store.take(Set.of("steve"))).containsExactly(exact);
    }

    @Test
    void aVoteIsHandedOutOnceAcrossServers() {
        ParkedVotes lobby = new JooqParkedVotes(dsl);
        ParkedVotes survival = new JooqParkedVotes(dsl);
        ParkedVote vote = new ParkedVote("Bea", "site-a", FIRST, false);
        lobby.park(vote);

        assertThat(survival.take(Set.of("Bea"))).containsExactly(vote);
        assertThat(lobby.take(Set.of("Bea"))).isEmpty();
    }

    @Test
    void votesParkedBeforeTheCutoffAreForgotten() {
        ParkedVotes store = new JooqParkedVotes(dsl);
        store.park(new ParkedVote("Old", "site-a", FIRST, false));
        ParkedVote fresh = new ParkedVote("Fresh", "site-a", SECOND, false);
        store.park(fresh);

        assertThat(store.forgetOlderThan(SECOND)).isEqualTo(1);

        assertThat(store.take(Set.of("Old"))).isEmpty();
        assertThat(store.take(Set.of("Fresh"))).containsExactly(fresh);
    }
}
