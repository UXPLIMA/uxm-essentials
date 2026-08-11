package com.uxplima.uxmessentials.ranks.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmRank;
import com.uxplima.uxmessentials.api.view.UxmRankStanding;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.PlayerRank;
import com.uxplima.uxmessentials.ranks.domain.Prestige;
import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.action.ActionDoubles;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.query.QueryDoubles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The published rank query: the ladder as configured, a player's standing with the rung above it, and whether the
 * next rung is within reach.
 *
 * <p>It runs on the action scheduler rather than the query one because {@code canRankUp} is the one read allowed
 * to touch the tick thread: a requirement can name the player's inventory or a placeholder, and neither can be
 * read off a worker. The test asserts that hop happens rather than taking it on trust.
 */
class RanksQueriesTest {

    private static final Rank FIRST = new Rank(RankId.of("first"), 10, "First", 0L, List.of(), List.of());
    private static final Rank CITIZEN =
            new Rank(RankId.of("citizen"), 20, "Citizen", 1000L, List.of("money 1000"), List.of());
    private static final Rank VIP = new Rank(RankId.of("vip"), 30, "VIP", 5000L, List.of(), List.of());
    private static final RankLadder LADDER = RankLadder.of(List.of(FIRST, CITIZEN, VIP));
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    private FakeRanks ranks;
    private ActionDoubles.InlineScheduler scheduler;
    private QueryDoubles.MapLookup players;

    @BeforeEach
    void setUp() {
        ranks = new FakeRanks();
        scheduler = new ActionDoubles.InlineScheduler();
        players = new QueryDoubles.MapLookup().with(ALICE);
    }

    @Test
    void theLadderIsTheConfiguredOneInOrder() {
        assertThat(queries(allowAll()).ladder()).extracting(UxmRank::id).containsExactly("first", "citizen", "vip");
        assertThat(queries(allowAll()).ladder()).contains(new UxmRank("citizen", "Citizen", 20, 1000L));
    }

    @Test
    void aStandingCarriesTheRungAboveItAndThePrestigeLevel() {
        ranks.put(ALICE.uuid(), new PlayerRank(RankId.of("citizen"), new Prestige(2)));

        Optional<UxmRankStanding> standing =
                queries(allowAll()).standingOf(ALICE.uuid()).join();

        assertThat(standing).isPresent();
        assertThat(standing.orElseThrow().rank().id()).isEqualTo("citizen");
        assertThat(standing.orElseThrow().next().orElseThrow().id()).isEqualTo("vip");
        assertThat(standing.orElseThrow().prestige()).isEqualTo(2);
        assertThat(standing.orElseThrow().atTop()).isFalse();
    }

    @Test
    void aPlayerOnTheTopRungHasNothingAboveThem() {
        ranks.put(ALICE.uuid(), new PlayerRank(RankId.of("vip"), Prestige.INITIAL));

        UxmRankStanding standing =
                queries(allowAll()).standingOf(ALICE.uuid()).join().orElseThrow();

        assertThat(standing.next()).isEmpty();
        assertThat(standing.atTop()).isTrue();
    }

    @Test
    void aPlayerWhoHasNeverBeenRankedStandsOnTheFirstRung() {
        UxmRankStanding standing =
                queries(allowAll()).standingOf(ALICE.uuid()).join().orElseThrow();

        assertThat(standing.rank().id()).isEqualTo("first");
    }

    @Test
    void theStandingReadGoesToAWorker() {
        queries(allowAll()).standingOf(ALICE.uuid()).join();

        assertThat(scheduler.asyncCalls()).isEqualTo(1);
    }

    @Test
    void reachIsCheckedOnThePlayersOwnThread() {
        assertThat(queries(allowAll()).canRankUp(ALICE.uuid()).join()).isTrue();
        assertThat(scheduler.entityCalls()).isEqualTo(1);
    }

    @Test
    void anUnmetRequirementPutsTheNextRungOutOfReach() {
        assertThat(queries(denyAll()).canRankUp(ALICE.uuid()).join()).isFalse();
    }

    @Test
    void thereIsNothingToReachFromTheTopRung() {
        ranks.put(ALICE.uuid(), new PlayerRank(RankId.of("vip"), Prestige.INITIAL));

        assertThat(queries(allowAll()).canRankUp(ALICE.uuid()).join()).isFalse();
    }

    @Test
    void anOfflinePlayerFailsClosedWithoutTouchingAnyThread() {
        assertThat(queries(allowAll()).canRankUp(UUID.randomUUID()).join()).isFalse();
        assertThat(scheduler.entityCalls()).isZero();
    }

    private RanksQueries queries(RankRequirementEvaluator requirements) {
        return new RanksQueries(new CurrentRank(ranks, LADDER), LADDER, requirements, players, scheduler);
    }

    private static RankRequirementEvaluator allowAll() {
        return (who, requirement) -> true;
    }

    private static RankRequirementEvaluator denyAll() {
        return (who, requirement) -> false;
    }

    /** The stored rank pointer, in memory. */
    private static final class FakeRanks implements PlayerRankRepository {

        private final Map<UUID, PlayerRank> stored = new HashMap<>();

        void put(UUID playerId, PlayerRank rank) {
            stored.put(playerId, rank);
        }

        @Override
        public Optional<PlayerRank> find(UUID playerId) {
            return Optional.ofNullable(stored.get(playerId));
        }

        @Override
        public void save(UUID playerId, RankId rankId, Prestige prestige) {
            stored.put(playerId, new PlayerRank(rankId, prestige));
        }
    }
}
