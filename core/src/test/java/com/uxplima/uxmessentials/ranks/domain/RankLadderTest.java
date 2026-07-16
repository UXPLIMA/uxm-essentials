package com.uxplima.uxmessentials.ranks.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure coverage of the {@link RankLadder} progression math: it sorts by order regardless of declaration order,
 * answers first / next / contains / orderedFrom, returns empty for the top rank's next, and rejects a duplicate
 * rank id.
 */
class RankLadderTest {

    private static final Rank FIRST = rank("first", 10);
    private static final Rank CITIZEN = rank("citizen", 20);
    private static final Rank VIP = rank("vip", 30);

    @Test
    void sortsRanksByOrderRegardlessOfDeclarationOrder() {
        RankLadder ladder = RankLadder.of(List.of(VIP, FIRST, CITIZEN));

        assertThat(ladder.ranks()).containsExactly(FIRST, CITIZEN, VIP);
        assertThat(ladder.first()).contains(FIRST);
    }

    @Test
    void nextReturnsTheRankImmediatelyAbove() {
        RankLadder ladder = RankLadder.of(List.of(FIRST, CITIZEN, VIP));

        assertThat(ladder.next(FIRST.id())).contains(CITIZEN);
        assertThat(ladder.next(CITIZEN.id())).contains(VIP);
    }

    @Test
    void nextAtTheTopRankIsEmpty() {
        RankLadder ladder = RankLadder.of(List.of(FIRST, CITIZEN, VIP));

        assertThat(ladder.next(VIP.id())).isEmpty();
    }

    @Test
    void nextForAnUnknownRankIsEmpty() {
        RankLadder ladder = RankLadder.of(List.of(FIRST, CITIZEN));

        assertThat(ladder.next(RankId.of("ghost"))).isEmpty();
    }

    @Test
    void containsAndRankResolveByIdAcrossTheLadder() {
        RankLadder ladder = RankLadder.of(List.of(FIRST, CITIZEN, VIP));

        assertThat(ladder.contains(CITIZEN.id())).isTrue();
        assertThat(ladder.contains(RankId.of("ghost"))).isFalse();
        assertThat(ladder.rank(VIP.id())).contains(VIP);
        assertThat(ladder.rank(RankId.of("ghost"))).isEmpty();
    }

    @Test
    void orderedFromReturnsTheChainFromARankToTheTop() {
        RankLadder ladder = RankLadder.of(List.of(FIRST, CITIZEN, VIP));

        assertThat(ladder.orderedFrom(FIRST.id())).containsExactly(FIRST, CITIZEN, VIP);
        assertThat(ladder.orderedFrom(CITIZEN.id())).containsExactly(CITIZEN, VIP);
        assertThat(ladder.orderedFrom(VIP.id())).containsExactly(VIP);
        assertThat(ladder.orderedFrom(RankId.of("ghost"))).isEmpty();
    }

    @Test
    void anEmptyLadderHasNoFirstRank() {
        RankLadder ladder = RankLadder.of(List.of());

        assertThat(ladder.isEmpty()).isTrue();
        assertThat(ladder.first()).isEmpty();
    }

    @Test
    void rejectsADuplicateRankId() {
        assertThatThrownBy(() -> RankLadder.of(List.of(FIRST, rank("first", 40))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate rank id");
    }

    private static Rank rank(String id, int order) {
        return new Rank(RankId.of(id), order, id, 0L, List.of(), List.of());
    }
}
