package com.uxplima.uxmessentials.ranks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.ranks.domain.RankStanding;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link CurrentRank}'s resolution of a stored pointer against the ladder: a player with no pointer stands
 * on the first rank at prestige zero, a stored id still on the ladder resolves to that rank with its prestige, a
 * stored id no longer on the ladder falls back to the first rank but keeps the prestige, and an empty ladder
 * yields no standing.
 */
class CurrentRankTest {

    private static final Rank FIRST = new Rank(RankId.of("first"), 10, "First", 0L, List.of(), List.of());
    private static final Rank CITIZEN = new Rank(RankId.of("citizen"), 20, "Citizen", 500L, List.of(), List.of());
    private static final RankLadder LADDER = RankLadder.of(List.of(FIRST, CITIZEN));
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void defaultsToTheFirstRankAtPrestigeZeroWhenNoPointerIsStored() {
        CurrentRank currentRank = new CurrentRank(new FakeRepository(Optional.empty()), LADDER);

        RankStanding standing = currentRank.of(PLAYER).orElseThrow();

        assertThat(standing.rank()).isEqualTo(FIRST);
        assertThat(standing.prestige()).isEqualTo(Prestige.INITIAL);
    }

    @Test
    void resolvesAStoredPointerStillOnTheLadder() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("citizen"), new Prestige(3))));
        CurrentRank currentRank = new CurrentRank(repo, LADDER);

        RankStanding standing = currentRank.of(PLAYER).orElseThrow();

        assertThat(standing.rank()).isEqualTo(CITIZEN);
        assertThat(standing.prestige()).isEqualTo(new Prestige(3));
    }

    @Test
    void fallsBackToTheFirstRankButKeepsPrestigeWhenTheStoredRankWasRemoved() {
        FakeRepository repo = new FakeRepository(Optional.of(new PlayerRank(RankId.of("legend"), new Prestige(2))));
        CurrentRank currentRank = new CurrentRank(repo, LADDER);

        RankStanding standing = currentRank.of(PLAYER).orElseThrow();

        assertThat(standing.rank()).isEqualTo(FIRST);
        assertThat(standing.prestige()).isEqualTo(new Prestige(2));
    }

    @Test
    void yieldsNoStandingWhenTheLadderIsEmpty() {
        CurrentRank currentRank = new CurrentRank(new FakeRepository(Optional.empty()), RankLadder.of(List.of()));

        assertThat(currentRank.of(PLAYER)).isEmpty();
    }

    /** A repository that returns a fixed stored pointer; the save path is not exercised by the read use case. */
    private record FakeRepository(Optional<PlayerRank> stored) implements PlayerRankRepository {
        @Override
        public Optional<PlayerRank> find(UUID playerId) {
            return stored;
        }

        @Override
        public void save(UUID playerId, RankId rankId, Prestige prestige) {
            throw new UnsupportedOperationException("the read use case does not save");
        }
    }
}
