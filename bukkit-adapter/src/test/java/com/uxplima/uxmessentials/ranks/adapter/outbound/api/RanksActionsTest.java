package com.uxplima.uxmessentials.ranks.adapter.outbound.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.action.UxmFailure;
import com.uxplima.uxmessentials.api.action.UxmOutcome;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.RanksConfig.PrestigeSettings;
import com.uxplima.uxmessentials.ranks.application.Rankup;
import com.uxplima.uxmessentials.ranks.application.SetRank;
import com.uxplima.uxmessentials.ranks.application.port.PlayerRankRepository;
import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
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
 * The published rank actions: a promotion another plugin asks for is the same promotion the command performs, a
 * refusal comes back as a code rather than an exception, and the two verbs that read a live player refuse for one
 * who is not there.
 */
class RanksActionsTest {

    private static final Rank FIRST = new Rank(RankId.of("first"), 10, "First", 0L, List.of(), List.of());
    // Citizen carries a requirement so a denying evaluator has something to deny; the cost stays zero so the
    // refusal under test is the requirement and not an empty wallet.
    private static final Rank CITIZEN =
            new Rank(RankId.of("citizen"), 20, "Citizen", 0L, List.of("money 1000"), List.of());
    private static final Rank VIP = new Rank(RankId.of("vip"), 30, "VIP", 0L, List.of(), List.of());
    private static final RankLadder LADDER = RankLadder.of(List.of(FIRST, CITIZEN, VIP));
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    private FakeRanks ranks;
    private ActionDoubles.InlineScheduler scheduler;
    private QueryDoubles.MapLookup players;
    private List<List<String>> ran;

    @BeforeEach
    void setUp() {
        ranks = new FakeRanks();
        scheduler = new ActionDoubles.InlineScheduler();
        players = new QueryDoubles.MapLookup().with(ALICE);
        ran = new ArrayList<>();
    }

    @Test
    void aRankupAdvancesThePointerOnThePlayersOwnThread() {
        UxmOutcome outcome = actions(allowAll()).rankUp(ALICE.uuid()).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(ranks.of(ALICE.uuid()).rankId()).isEqualTo(RankId.of("citizen"));
        assertThat(scheduler.entityCalls()).isEqualTo(1);
    }

    @Test
    void anUnmetRequirementIsARefusalRatherThanAFault() {
        UxmOutcome outcome = actions(denyAll()).rankUp(ALICE.uuid()).join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(ranks.stored).isEmpty();
    }

    @Test
    void aPlayerAlreadyOnTheTopRungHasNowhereToGo() {
        ranks.put(ALICE.uuid(), new PlayerRank(RankId.of("vip"), Prestige.INITIAL));

        UxmOutcome outcome = actions(allowAll()).rankUp(ALICE.uuid()).join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.ALREADY_IN_STATE);
    }

    @Test
    void aRankupForSomebodyWhoIsAwayIsRefusedWithoutTouchingTheirRank() {
        UxmOutcome outcome = actions(allowAll()).rankUp(UUID.randomUUID()).join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.PLAYER_OFFLINE);
        assertThat(scheduler.entityCalls()).isZero();
        assertThat(ranks.stored).isEmpty();
    }

    @Test
    void aDirectSetWritesTheRankOnAWorkerAndWorksForAnOfflineAccount() {
        UUID absent = UUID.randomUUID();

        UxmOutcome outcome = actions(denyAll()).setRank(absent, "vip").join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(ranks.of(absent).rankId()).isEqualTo(RankId.of("vip"));
        assertThat(scheduler.asyncCalls()).isEqualTo(1);
    }

    @Test
    void aSetToARankThatIsNotOnTheLadderIsNotFound() {
        UxmOutcome outcome =
                actions(allowAll()).setRank(ALICE.uuid(), "emperor").join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.NOT_FOUND);
        assertThat(ranks.stored).isEmpty();
    }

    @Test
    void aPrestigeResetsThePointerAndCountsTheLevel() {
        ranks.put(ALICE.uuid(), new PlayerRank(RankId.of("vip"), Prestige.INITIAL));

        UxmOutcome outcome = actions(allowAll()).prestige(ALICE.uuid()).join();

        assertThat(outcome.succeeded()).isTrue();
        assertThat(ranks.of(ALICE.uuid())).isEqualTo(new PlayerRank(RankId.of("first"), new Prestige(1)));
    }

    @Test
    void aPrestigeBelowTheTopRungIsRefused() {
        UxmOutcome outcome = actions(allowAll()).prestige(ALICE.uuid()).join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.ALREADY_IN_STATE);
    }

    @Test
    void prestigeSwitchedOffRefusesRatherThanPretendingItRan() {
        ranks.put(ALICE.uuid(), new PlayerRank(RankId.of("vip"), Prestige.INITIAL));

        UxmOutcome outcome = new RanksActions(writes(allowAll(), Optional.empty()), players, scheduler)
                .prestige(ALICE.uuid())
                .join();

        assertThat(outcome.failure().orElseThrow().code()).isEqualTo(UxmFailure.REFUSED);
        assertThat(ranks.of(ALICE.uuid()).rankId()).isEqualTo(RankId.of("vip"));
    }

    @Test
    void aRankupRunsTheRanksOwnActionsJustLikeTheCommandDoes() {
        RankLadder acting = RankLadder.of(List.of(
                FIRST, new Rank(RankId.of("citizen"), 20, "Citizen", 0L, List.of(), List.of("message welcome"))));
        Rankup rankup = new Rankup(
                new CurrentRank(ranks, acting),
                ranks,
                acting,
                allowAll(),
                recordingRunner(),
                Optional.<RankEconomy>empty(),
                event -> {});
        RanksApiWrites writes = new RanksApiWrites(rankup, new SetRank(ranks, acting, event -> {}), Optional.empty());

        new RanksActions(writes, players, scheduler).rankUp(ALICE.uuid()).join();

        assertThat(ran).containsExactly(List.of("message welcome"));
    }

    private RanksActions actions(RankRequirementEvaluator requirements) {
        PrestigeSettings settings = new PrestigeSettings(true, 0, 0L, List.of(), List.of(), 1.0);
        com.uxplima.uxmessentials.ranks.application.Prestige prestige =
                new com.uxplima.uxmessentials.ranks.application.Prestige(
                        new CurrentRank(ranks, LADDER),
                        ranks,
                        LADDER,
                        requirements,
                        recordingRunner(),
                        Optional.<RankEconomy>empty(),
                        settings,
                        event -> {});
        return new RanksActions(writes(requirements, Optional.of(prestige)), players, scheduler);
    }

    private RanksApiWrites writes(
            RankRequirementEvaluator requirements,
            Optional<com.uxplima.uxmessentials.ranks.application.Prestige> prestige) {
        Rankup rankup = new Rankup(
                new CurrentRank(ranks, LADDER),
                ranks,
                LADDER,
                requirements,
                recordingRunner(),
                Optional.<RankEconomy>empty(),
                event -> {});
        return new RanksApiWrites(rankup, new SetRank(ranks, LADDER, event -> {}), prestige);
    }

    private RankActionRunner recordingRunner() {
        return (who, lines) -> ran.add(List.copyOf(lines));
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

        /** What is stored for this player, failing the test rather than the null check when nothing is. */
        PlayerRank of(UUID playerId) {
            PlayerRank stored = this.stored.get(playerId);
            if (stored == null) {
                throw new AssertionError("no rank stored for " + playerId);
            }
            return stored;
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
