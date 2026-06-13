package com.uxplima.uxmessentials.vote.domain.reward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Constructor validation and convenience behaviour of the reward model records: {@link ItemReward},
 * {@link RewardSpec}, {@link MilestoneReward}, and the {@link RewardCatalog} per-site lookup. Each record
 * must reject the inputs the engine and config parser depend on never reaching it, and the convenience
 * predicates must match their documented contract.
 */
class RewardModelValidationTest {

    @Test
    void itemRewardRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> new ItemReward("DIAMOND", 0, Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemRewardRejectsBlankMaterial() {
        assertThatThrownBy(() -> new ItemReward("  ", 1, Optional.empty(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemRewardCopiesLoreDefensively() {
        List<String> lore = new java.util.ArrayList<>(List.of("line one"));
        ItemReward reward = new ItemReward("DIAMOND", 1, Optional.of("Shiny"), lore);
        lore.add("mutated");
        assertThat(reward.lore()).containsExactly("line one");
    }

    @Test
    void rewardSpecRejectsChanceOutOfRange() {
        assertThatThrownBy(() -> spec(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> spec(101)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rewardSpecAcceptsTheChanceBoundaries() {
        assertThat(spec(0).chancePercent()).isZero();
        assertThat(spec(100).chancePercent()).isEqualTo(100);
    }

    @Test
    void rewardSpecAppliesInAnyWorldWithAnEmptyFilter() {
        assertThat(spec(100).appliesInWorld("anywhere")).isTrue();
    }

    @Test
    void rewardSpecAppliesOnlyInListedWorldsWithAFilter() {
        RewardSpec filtered =
                new RewardSpec(100, Optional.empty(), List.of(), List.of(), List.of(), List.of(), Set.of("nether"));
        assertThat(filtered.appliesInWorld("nether")).isTrue();
        assertThat(filtered.appliesInWorld("world")).isFalse();
    }

    @Test
    void milestoneRejectsBothAtAndEvery() {
        assertThatThrownBy(() -> new MilestoneReward(OptionalLong.of(10), OptionalLong.of(5), spec(100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void milestoneRejectsNeitherAtNorEvery() {
        assertThatThrownBy(() -> new MilestoneReward(OptionalLong.empty(), OptionalLong.empty(), spec(100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void milestoneRejectsNonPositiveThresholds() {
        assertThatThrownBy(() -> new MilestoneReward(OptionalLong.of(0), OptionalLong.empty(), spec(100)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MilestoneReward(OptionalLong.empty(), OptionalLong.of(-3), spec(100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void milestoneAtMatchesOnlyTheExactCount() {
        MilestoneReward at = new MilestoneReward(OptionalLong.of(50), OptionalLong.empty(), spec(100));
        assertThat(at.matches(50)).isTrue();
        assertThat(at.matches(49)).isFalse();
        assertThat(at.matches(100)).isFalse();
    }

    @Test
    void milestoneEveryMatchesPositiveMultiplesOnly() {
        MilestoneReward every = new MilestoneReward(OptionalLong.empty(), OptionalLong.of(25), spec(100));
        assertThat(every.matches(25)).isTrue();
        assertThat(every.matches(75)).isTrue();
        assertThat(every.matches(0)).isFalse();
        assertThat(every.matches(26)).isFalse();
    }

    @Test
    void streakRejectsBothAtAndEvery() {
        assertThatThrownBy(() -> new StreakReward(OptionalLong.of(10), OptionalLong.of(5), spec(100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void streakRejectsNeitherAtNorEvery() {
        assertThatThrownBy(() -> new StreakReward(OptionalLong.empty(), OptionalLong.empty(), spec(100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void streakRejectsNonPositiveThresholds() {
        assertThatThrownBy(() -> new StreakReward(OptionalLong.of(0), OptionalLong.empty(), spec(100)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StreakReward(OptionalLong.empty(), OptionalLong.of(-3), spec(100)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void streakAtMatchesOnlyTheExactLength() {
        StreakReward at = new StreakReward(OptionalLong.of(7), OptionalLong.empty(), spec(100));
        assertThat(at.matches(7)).isTrue();
        assertThat(at.matches(6)).isFalse();
        assertThat(at.matches(14)).isFalse();
    }

    @Test
    void streakEveryMatchesPositiveMultiplesOnly() {
        StreakReward every = new StreakReward(OptionalLong.empty(), OptionalLong.of(5), spec(100));
        assertThat(every.matches(5)).isTrue();
        assertThat(every.matches(15)).isTrue();
        assertThat(every.matches(0)).isFalse();
        assertThat(every.matches(6)).isFalse();
    }

    @Test
    void catalogForSiteIsCaseInsensitiveAndEmptyWhenUnknown() {
        RewardCatalog catalog =
                new RewardCatalog(List.of(), Map.of("TopG", List.of(spec(100))), List.of(), List.of(), List.of());
        assertThat(catalog.forSite("topg")).hasSize(1);
        assertThat(catalog.forSite("TOPG")).hasSize(1);
        assertThat(catalog.forSite("unknown")).isEmpty();
    }

    @Test
    void emptyCatalogHasNoRewards() {
        RewardCatalog empty = RewardCatalog.empty();
        assertThat(empty.perVote()).isEmpty();
        assertThat(empty.firstVote()).isEmpty();
        assertThat(empty.milestones()).isEmpty();
        assertThat(empty.streaks()).isEmpty();
        assertThat(empty.forSite("any")).isEmpty();
    }

    private static RewardSpec spec(int chance) {
        return new RewardSpec(chance, Optional.empty(), List.of(), List.of(), List.of(), List.of(), Set.of());
    }
}
