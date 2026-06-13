package com.uxplima.uxmessentials.vote.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.reward.MilestoneReward;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import com.uxplima.uxmessentials.vote.domain.reward.StreakReward;
import org.junit.jupiter.api.Test;

/**
 * The {@link RewardEngine}'s resolution and gating, driven with deterministic chance/permission stubs and
 * crafted {@link VoteTally} totals so every gate and trigger is exercised without RNG. Covers chance
 * gating (roll pass vs fail), permission gating, the world filter (offline + non-matching world + empty
 * filter), first-vote-only firing, milestone {@code at}/{@code every} matching, case-insensitive per-site
 * matching, and a combined case where several reward sets contribute at once.
 */
class RewardEngineTest {

    private static final PlayerRef VOTER = new PlayerRef(UUID.randomUUID(), "Voter");

    private static boolean always(int chance) {
        return true;
    }

    private static boolean never(int chance) {
        return false;
    }

    private static boolean allPerms(String node) {
        return true;
    }

    private static boolean noPerms(String node) {
        return false;
    }

    @Test
    void perVoteSpecIsGrantedWhenTheChanceRollPasses() {
        RewardEngine engine = engine(perVote(spec("give x")));

        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(commandsOf(grants)).containsExactly("give x");
    }

    @Test
    void perVoteSpecIsSkippedWhenTheChanceRollFails() {
        RewardEngine engine = engine(perVote(spec("give x")));

        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::never));

        assertThat(grants).isEmpty();
    }

    @Test
    void specIsSkippedWhenTheRequiredPermissionIsAbsent() {
        RewardSpec gated = new RewardSpec(
                100,
                Optional.of("uxmessentials.vote.bonus"),
                List.of("give x"),
                List.of(),
                List.of(),
                List.of(),
                Set.of());
        RewardEngine engine = engine(perVote(gated));

        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "world", RewardEngineTest::noPerms, RewardEngineTest::always));

        assertThat(grants).isEmpty();
    }

    @Test
    void worldFilteredSpecIsSkippedWhenTheVoterIsNotInAListedWorld() {
        RewardSpec worldGated = new RewardSpec(
                100, Optional.empty(), List.of("give x"), List.of(), List.of(), List.of(), Set.of("nether"));
        RewardEngine engine = engine(perVote(worldGated));

        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(grants).isEmpty();
    }

    @Test
    void worldFilteredSpecIsSkippedForAnOfflineVoter() {
        RewardSpec worldGated = new RewardSpec(
                100, Optional.empty(), List.of("give x"), List.of(), List.of(), List.of(), Set.of("world"));
        RewardEngine engine = engine(perVote(worldGated));

        List<RewardGrant> grants =
                engine.resolve(ctx(tally(1), "site", false, "", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(grants).isEmpty();
    }

    @Test
    void emptyWorldFilterPassesForAnOfflineVoter() {
        RewardEngine engine = engine(perVote(spec("give x")));

        List<RewardGrant> grants =
                engine.resolve(ctx(tally(1), "site", false, "", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(commandsOf(grants)).containsExactly("give x");
    }

    @Test
    void firstVoteSpecOnlyFiresWhenTheAllTimeCountIsOne() {
        RewardCatalog catalog = new RewardCatalog(List.of(), Map.of(), List.of(spec("welcome")), List.of(), List.of());
        RewardEngine engine = new RewardEngine(catalog, Set.of());

        assertThat(commandsOf(engine.resolve(
                        ctx(tally(1), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always))))
                .containsExactly("welcome");
        assertThat(engine.resolve(
                        ctx(tally(2), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always)))
                .isEmpty();
    }

    @Test
    void milestoneAtFiresOnlyOnTheExactCount() {
        MilestoneReward at100 = new MilestoneReward(OptionalLong.of(100), OptionalLong.empty(), spec("milestone-100"));
        RewardEngine engine = new RewardEngine(
                new RewardCatalog(List.of(), Map.of(), List.of(), List.of(at100), List.of()), Set.of());

        assertThat(commandsOf(engine.resolve(
                        ctx(tally(100), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always))))
                .containsExactly("milestone-100");
        assertThat(engine.resolve(
                        ctx(tally(99), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always)))
                .isEmpty();
        assertThat(engine.resolve(
                        ctx(tally(101), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always)))
                .isEmpty();
    }

    @Test
    void milestoneEveryFiresOnEveryMultiple() {
        MilestoneReward every10 = new MilestoneReward(OptionalLong.empty(), OptionalLong.of(10), spec("milestone-10s"));
        RewardEngine engine = new RewardEngine(
                new RewardCatalog(List.of(), Map.of(), List.of(), List.of(every10), List.of()), Set.of());

        assertThat(commandsOf(engine.resolve(
                        ctx(tally(10), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always))))
                .containsExactly("milestone-10s");
        assertThat(commandsOf(engine.resolve(
                        ctx(tally(30), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always))))
                .containsExactly("milestone-10s");
        assertThat(engine.resolve(
                        ctx(tally(15), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always)))
                .isEmpty();
    }

    @Test
    void perSiteMatchingIsCaseInsensitive() {
        RewardCatalog catalog = new RewardCatalog(
                List.of(), Map.of("PlanetMinecraft", List.of(spec("site-bonus"))), List.of(), List.of(), List.of());
        RewardEngine engine = new RewardEngine(catalog, Set.of());

        assertThat(commandsOf(engine.resolve(ctx(
                        tally(5),
                        "planetminecraft",
                        true,
                        "world",
                        RewardEngineTest::allPerms,
                        RewardEngineTest::always))))
                .containsExactly("site-bonus");
        assertThat(engine.resolve(ctx(
                        tally(5), "OtherSite", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always)))
                .isEmpty();
    }

    @Test
    void severalRewardSetsContributeInOrder() {
        MilestoneReward every1 = new MilestoneReward(OptionalLong.empty(), OptionalLong.of(1), spec("milestone"));
        RewardCatalog catalog = new RewardCatalog(
                List.of(spec("per-vote")),
                Map.of("site", List.of(spec("per-site"))),
                List.of(spec("first-vote")),
                List.of(every1),
                List.of());
        RewardEngine engine = new RewardEngine(catalog, Set.of());

        // alltime == 1 fires all four sets; order is per-vote, site, first-vote, milestones.
        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(commandsOf(grants)).containsExactly("per-vote", "per-site", "first-vote", "milestone");
    }

    @Test
    void streakRewardFiresWhenTheStreakAdvancedAndMatches() {
        StreakReward at7 = new StreakReward(OptionalLong.of(7), OptionalLong.empty(), spec("streak-7"));
        RewardEngine engine =
                new RewardEngine(new RewardCatalog(List.of(), Map.of(), List.of(), List.of(), List.of(at7)), Set.of());

        assertThat(commandsOf(engine.resolve(streakCtx(streakTally(7), true)))).containsExactly("streak-7");
    }

    @Test
    void streakRewardDoesNotFireWhenTheStreakDidNotAdvanceEvenIfEveryMatches() {
        StreakReward every1 = new StreakReward(OptionalLong.empty(), OptionalLong.of(1), spec("streak-daily"));
        RewardEngine engine = new RewardEngine(
                new RewardCatalog(List.of(), Map.of(), List.of(), List.of(), List.of(every1)), Set.of());

        // A same-day re-vote leaves streakAdvanced false, so the recurring streak reward must not fire.
        assertThat(engine.resolve(streakCtx(streakTally(3), false))).isEmpty();
        // The same streak length, when it just advanced, does fire.
        assertThat(commandsOf(engine.resolve(streakCtx(streakTally(3), true)))).containsExactly("streak-daily");
    }

    @Test
    void streakRewardEveryFiresOnEachMultiple() {
        StreakReward every5 = new StreakReward(OptionalLong.empty(), OptionalLong.of(5), spec("streak-5s"));
        RewardEngine engine = new RewardEngine(
                new RewardCatalog(List.of(), Map.of(), List.of(), List.of(), List.of(every5)), Set.of());

        assertThat(commandsOf(engine.resolve(streakCtx(streakTally(5), true)))).containsExactly("streak-5s");
        assertThat(commandsOf(engine.resolve(streakCtx(streakTally(10), true)))).containsExactly("streak-5s");
        assertThat(engine.resolve(streakCtx(streakTally(7), true))).isEmpty();
    }

    @Test
    void streakRewardStillGatesOnChance() {
        StreakReward at3 = new StreakReward(
                OptionalLong.of(3),
                OptionalLong.empty(),
                new RewardSpec(50, Optional.empty(), List.of("streak-3"), List.of(), List.of(), List.of(), Set.of()));
        RewardEngine engine =
                new RewardEngine(new RewardCatalog(List.of(), Map.of(), List.of(), List.of(), List.of(at3)), Set.of());

        RewardContext failingRoll = new RewardContext(
                VOTER,
                true,
                "world",
                "site",
                streakTally(3),
                true,
                RewardEngineTest::allPerms,
                RewardEngineTest::never);
        assertThat(engine.resolve(failingRoll)).isEmpty();
    }

    @Test
    void aVoteCastInAGloballyDisabledWorldEarnsNothing() {
        RewardEngine engine = new RewardEngine(
                new RewardCatalog(perVote(spec("give x")), Map.of(), List.of(), List.of(), List.of()),
                Set.of("creative"));

        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "creative", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(grants).isEmpty();
    }

    @Test
    void aVoteCastOutsideTheDisabledSetIsUnaffected() {
        RewardEngine engine = new RewardEngine(
                new RewardCatalog(perVote(spec("give x")), Map.of(), List.of(), List.of(), List.of()),
                Set.of("creative"));

        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(commandsOf(grants)).containsExactly("give x");
    }

    @Test
    void theGlobalDisabledWorldMatchIsCaseInsensitive() {
        RewardEngine engine = new RewardEngine(
                new RewardCatalog(perVote(spec("give x")), Map.of(), List.of(), List.of(), List.of()),
                Set.of("Creative"));

        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "creative", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(grants).isEmpty();
    }

    @Test
    void anEmptyDisabledWorldSetLeavesEveryWorldEnabled() {
        RewardEngine engine = new RewardEngine(
                new RewardCatalog(perVote(spec("give x")), Map.of(), List.of(), List.of(), List.of()), Set.of());

        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "creative", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(commandsOf(grants)).containsExactly("give x");
    }

    private static RewardEngine engine(List<RewardSpec> perVote) {
        return new RewardEngine(new RewardCatalog(perVote, Map.of(), List.of(), List.of(), List.of()), Set.of());
    }

    private static List<RewardSpec> perVote(RewardSpec spec) {
        return List.of(spec);
    }

    private static RewardSpec spec(String command) {
        return new RewardSpec(100, Optional.empty(), List.of(command), List.of(), List.of(), List.of(), Set.of());
    }

    private static RewardContext ctx(
            VoteTally tally, String service, boolean online, String world, Predicate<String> perms, IntPredicate roll) {
        return new RewardContext(VOTER, online, world, service, tally, false, perms, roll);
    }

    private static RewardContext streakCtx(VoteTally tally, boolean streakAdvanced) {
        return new RewardContext(
                VOTER,
                true,
                "world",
                "site",
                tally,
                streakAdvanced,
                RewardEngineTest::allPerms,
                RewardEngineTest::always);
    }

    private static VoteTally tally(long alltime) {
        return new VoteTally(alltime, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static VoteTally streakTally(long currentStreak) {
        return new VoteTally(1L, 0L, 0L, 0L, 0L, 0L, 0L, currentStreak, currentStreak, 0L);
    }

    private static List<String> commandsOf(List<RewardGrant> grants) {
        return grants.stream().flatMap(g -> g.commands().stream()).toList();
    }
}
