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
        RewardCatalog catalog = new RewardCatalog(List.of(), Map.of(), List.of(spec("welcome")), List.of());
        RewardEngine engine = new RewardEngine(catalog);

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
        RewardEngine engine = new RewardEngine(new RewardCatalog(List.of(), Map.of(), List.of(), List.of(at100)));

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
        RewardEngine engine = new RewardEngine(new RewardCatalog(List.of(), Map.of(), List.of(), List.of(every10)));

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
                List.of(), Map.of("PlanetMinecraft", List.of(spec("site-bonus"))), List.of(), List.of());
        RewardEngine engine = new RewardEngine(catalog);

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
                List.of(every1));
        RewardEngine engine = new RewardEngine(catalog);

        // alltime == 1 fires all four sets; order is per-vote, site, first-vote, milestones.
        List<RewardGrant> grants = engine.resolve(
                ctx(tally(1), "site", true, "world", RewardEngineTest::allPerms, RewardEngineTest::always));

        assertThat(commandsOf(grants)).containsExactly("per-vote", "per-site", "first-vote", "milestone");
    }

    private static RewardEngine engine(List<RewardSpec> perVote) {
        return new RewardEngine(new RewardCatalog(perVote, Map.of(), List.of(), List.of()));
    }

    private static List<RewardSpec> perVote(RewardSpec spec) {
        return List.of(spec);
    }

    private static RewardSpec spec(String command) {
        return new RewardSpec(100, Optional.empty(), List.of(command), List.of(), List.of(), List.of(), Set.of());
    }

    private static RewardContext ctx(
            VoteTally tally, String service, boolean online, String world, Predicate<String> perms, IntPredicate roll) {
        return new RewardContext(VOTER, online, world, service, tally, perms, roll);
    }

    private static VoteTally tally(long alltime) {
        return new VoteTally(alltime, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static List<String> commandsOf(List<RewardGrant> grants) {
        return grants.stream().flatMap(g -> g.commands().stream()).toList();
    }
}
