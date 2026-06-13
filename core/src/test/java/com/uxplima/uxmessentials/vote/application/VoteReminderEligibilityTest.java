package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;
import org.junit.jupiter.api.Test;

class VoteReminderEligibilityTest {

    private static final Instant NOW = Instant.ofEpochSecond(2_000_000);
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final Duration TWELVE_HOURS = Duration.ofHours(12);

    @Test
    void emptyCatalogReturnsFalse() {
        VoteReminderEligibility elig = new VoteReminderEligibility(new NoLastVoteRepo(), VoteSiteCatalog.empty());
        assertThat(elig.canVoteSomewhere(ALICE, NOW)).isFalse();
    }

    @Test
    void neverVotedMeansCanVote() {
        VoteSiteCatalog catalog = catalogOf(site("TopVoter", TWELVE_HOURS));
        VoteReminderEligibility elig = new VoteReminderEligibility(new NoLastVoteRepo(), catalog);
        assertThat(elig.canVoteSomewhere(ALICE, NOW)).isTrue();
    }

    @Test
    void allSitesOnCooldownReturnsFalse() {
        VoteSiteCatalog catalog = catalogOf(site("TopVoter", TWELVE_HOURS));
        // Voted just now → all on cooldown.
        StubRepo repo = new StubRepo(Map.of("TopVoter", NOW));
        VoteReminderEligibility elig = new VoteReminderEligibility(repo, catalog);
        assertThat(elig.canVoteSomewhere(ALICE, NOW)).isFalse();
    }

    @Test
    void oneAvailableSiteReturnsTrue() {
        VoteSiteCatalog catalog = catalogOf(site("TopVoter", TWELVE_HOURS), site("VoteMC", TWELVE_HOURS));
        // TopVoter on cooldown, VoteMC never voted.
        StubRepo repo = new StubRepo(Map.of("TopVoter", NOW));
        VoteReminderEligibility elig = new VoteReminderEligibility(repo, catalog);
        assertThat(elig.canVoteSomewhere(ALICE, NOW)).isTrue();
    }

    private static VoteSiteSpec site(String name, Duration cooldown) {
        return new VoteSiteSpec(name, Optional.empty(), cooldown);
    }

    private static VoteSiteCatalog catalogOf(VoteSiteSpec... specs) {
        return new VoteSiteCatalog(List.of(specs));
    }

    private static final class NoLastVoteRepo extends FakeVoteRepositoryBase {
        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.empty();
        }
    }

    private static final class StubRepo extends FakeVoteRepositoryBase {
        private final Map<String, Instant> votes;

        StubRepo(Map<String, Instant> votes) {
            this.votes = votes;
        }

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.ofNullable(votes.get(site));
        }
    }
}
