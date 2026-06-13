package com.uxplima.uxmessentials.persistence.vote;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqVoteRepository} against the embedded SQLite backend with the Flyway
 * baseline. Covers the atomic party-counter increment, offline-reward queue ordering and drain-once
 * semantics, and the V24 {@code vote_totals} table: totals round-trip, upsert idempotency, leaderboard
 * ordering and limit, empty-DB behaviour, and unknown-player defaults.
 */
class JooqVoteRepositoryTest {

    private Persistence persistence;
    private JooqVoteRepository repository;
    private PlayerRef bob;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqVoteRepository(persistence.dsl());
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void incrementReturnsThePostIncrementValueAndPersistsIt() {
        assertThat(repository.partyCount()).isZero();

        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(1);
        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(2);
        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(3);

        assertThat(repository.partyCount()).isEqualTo(3);
    }

    @Test
    void incrementContinuesFromAnExplicitlySetCount() {
        repository.setPartyCount(24);

        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(25);
        assertThat(repository.partyCount()).isEqualTo(25);
    }

    @Test
    void successiveEnqueuesForOnePlayerKeepClimbingTheIndexWithoutColliding() {
        repository.enqueue(reward(bob, "give {player} diamond 1"));
        repository.enqueue(reward(bob, "give {player} emerald 2"));
        repository.enqueue(reward(bob, "give {player} apple 3"));

        assertThat(repository.hasPending(bob)).isTrue();

        List<QueuedReward> drained = repository.drainFor(bob);

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).commands())
                .containsExactly("give {player} diamond 1", "give {player} emerald 2", "give {player} apple 3");
        assertThat(repository.hasPending(bob)).isFalse();
    }

    @Test
    void aMultiCommandBatchKeepsItsOrderAcrossADrain() {
        repository.enqueue(new QueuedReward(bob, List.of("first", "second", "third"), Instant.EPOCH));

        List<QueuedReward> drained = repository.drainFor(bob);

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).commands()).containsExactly("first", "second", "third");
    }

    // -------------------------------------------------------------------------
    // vote_totals: totalsOf / saveTotals / topVoters
    // -------------------------------------------------------------------------

    @Test
    void totalsOfUnknownPlayerReturnsEmpty() {
        PlayerRef stranger = new PlayerRef(UUID.randomUUID(), "Stranger");
        assertThat(repository.totalsOf(stranger)).isEqualTo(VoteTally.empty());
    }

    @Test
    void saveTotalsRoundTrip() {
        VoteTally tally = new VoteTally(42L, 3L, 7L, 12L, 19900L, 202401L, 24289L);
        repository.saveTotals(bob, tally);
        assertThat(repository.totalsOf(bob)).isEqualTo(tally);
    }

    @Test
    void saveTotalsUpsertsWithoutDuplicating() {
        VoteTally first = new VoteTally(1L, 1L, 1L, 1L, 100L, 202401L, 24277L);
        VoteTally second = new VoteTally(2L, 2L, 2L, 2L, 101L, 202401L, 24278L);

        repository.saveTotals(bob, first);
        repository.saveTotals(bob, second);

        assertThat(repository.totalsOf(bob)).isEqualTo(second);
    }

    @Test
    void topVotersReturnsPlayersOrderedByPeriodColumnDescending() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef charlie = new PlayerRef(UUID.randomUUID(), "Charlie");

        // Monthly: alice=5, bob=3, charlie=10 — expected order: charlie, alice, bob.
        repository.saveTotals(alice, new VoteTally(10L, 2L, 4L, 5L, 100L, 202401L, 24277L));
        repository.saveTotals(bob, new VoteTally(5L, 1L, 2L, 3L, 100L, 202401L, 24277L));
        repository.saveTotals(charlie, new VoteTally(15L, 4L, 8L, 10L, 100L, 202401L, 24277L));

        List<VoteRanking> top = repository.topVoters(VotePeriod.MONTHLY, 10);

        assertThat(top).extracting(VoteRanking::votes).containsExactly(10L, 5L, 3L);
    }

    @Test
    void topVotersRespectsLimit() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef charlie = new PlayerRef(UUID.randomUUID(), "Charlie");

        repository.saveTotals(alice, new VoteTally(10L, 2L, 4L, 5L, 100L, 202401L, 24277L));
        repository.saveTotals(bob, new VoteTally(5L, 1L, 2L, 3L, 100L, 202401L, 24277L));
        repository.saveTotals(charlie, new VoteTally(15L, 4L, 8L, 10L, 100L, 202401L, 24277L));

        List<VoteRanking> top = repository.topVoters(VotePeriod.MONTHLY, 2);

        assertThat(top).hasSize(2);
        assertThat(top.get(0).votes()).isEqualTo(10L);
        assertThat(top.get(1).votes()).isEqualTo(5L);
    }

    @Test
    void topVotersExcludesZeroCountRows() {
        // A player whose alltime count is zero should not appear in the leaderboard.
        repository.saveTotals(bob, new VoteTally(0L, 0L, 0L, 0L, 0L, 0L, 0L));

        List<VoteRanking> top = repository.topVoters(VotePeriod.ALLTIME, 10);

        assertThat(top).isEmpty();
    }

    @Test
    void topVotersOnEmptyDatabaseReturnsEmptyList() {
        assertThat(repository.topVoters(VotePeriod.WEEKLY, 10)).isEmpty();
    }

    @Test
    void topVotersAlltimePeriodOrdersByAlltimeColumn() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");

        repository.saveTotals(alice, new VoteTally(100L, 1L, 1L, 1L, 100L, 202401L, 24277L));
        repository.saveTotals(bob, new VoteTally(200L, 1L, 1L, 1L, 100L, 202401L, 24277L));

        List<VoteRanking> top = repository.topVoters(VotePeriod.ALLTIME, 10);

        assertThat(top).extracting(VoteRanking::votes).containsExactly(200L, 100L);
    }

    private static QueuedReward reward(PlayerRef player, String command) {
        return new QueuedReward(player, List.of(command), Instant.EPOCH);
    }

    /** A config that selects the embedded SQLite backend with every default — no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
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
