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
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqVoteRepository} against the embedded SQLite backend with the Flyway V15
 * baseline. It proves the atomic party-counter increment returns the post-increment value (and persists it),
 * that an enqueue derives each row's {@code idx} from {@code MAX(idx)+1} inside the insert so successive
 * batches for one player keep climbing without a read-then-insert window, and that a drain returns the queued
 * commands in order and removes them so a batch pays out exactly once.
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
