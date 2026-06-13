package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShowLastVoteTest {

    private static final Instant NOW = Instant.ofEpochSecond(2_000_000);
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    private StubRepository repository;
    private ShowNextVoteTest.CapturingNotifier notifier;

    @BeforeEach
    void setUp() {
        repository = new StubRepository();
        notifier = new ShowNextVoteTest.CapturingNotifier();
    }

    @Test
    void emptyCatalogSendsNoneMessage() {
        ShowLastVote use = new ShowLastVote(repository, VoteSiteCatalog.empty(), notifier.voteNotifier());
        use.show(ALICE, NOW);
        assertThat(notifier.keys).containsExactly(VoteMessageKey.VOTE_NEXT_NONE);
    }

    @Test
    void neverVotedSiteShowsNeverEntry() {
        VoteSiteCatalog catalog =
                new VoteSiteCatalog(List.of(new VoteSiteSpec("TopVoter", Optional.empty(), Duration.ofHours(12))));

        ShowLastVote use = new ShowLastVote(repository, catalog, notifier.voteNotifier());
        use.show(ALICE, NOW);

        assertThat(notifier.keys).contains(VoteMessageKey.VOTE_LAST_HEADER, VoteMessageKey.VOTE_LAST_NEVER);
        assertThat(notifier.placeholderFor(VoteMessageKey.VOTE_LAST_NEVER, "site"))
                .isEqualTo("TopVoter");
    }

    @Test
    void votedSiteShowsElapsedTime() {
        VoteSiteCatalog catalog =
                new VoteSiteCatalog(List.of(new VoteSiteSpec("TopVoter", Optional.empty(), Duration.ofHours(12))));
        // Voted 3 hours ago.
        repository.lastVote = Optional.of(NOW.minus(Duration.ofHours(3)));

        ShowLastVote use = new ShowLastVote(repository, catalog, notifier.voteNotifier());
        use.show(ALICE, NOW);

        assertThat(notifier.keys).contains(VoteMessageKey.VOTE_LAST_HEADER, VoteMessageKey.VOTE_LAST_ENTRY);
        assertThat(notifier.placeholderFor(VoteMessageKey.VOTE_LAST_ENTRY, "time"))
                .isEqualTo("3h");
    }

    private static final class StubRepository extends FakeVoteRepositoryBase {
        Optional<Instant> lastVote = Optional.empty();

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return lastVote;
        }
    }
}
