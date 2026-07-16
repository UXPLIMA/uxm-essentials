package com.uxplima.uxmessentials.ranks.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure requirement grammar: a leading keyword picks the {@link RankRequirementType} and the remainder is
 * carried verbatim as the value, the {@code prev-rank} alias resolves, a value that spans several tokens (a
 * placeholder comparison) is kept whole, and a keyword-only or unrecognised entry parses to empty so the ladder
 * codec skips it rather than failing the whole rank.
 */
class RankRequirementTest {

    @Test
    void parsesATypedRequirementIntoItsKeywordAndVerbatimValue() {
        RankRequirement money = RankRequirement.parse("money 1000").orElseThrow();
        assertThat(money.type()).isEqualTo(RankRequirementType.MONEY);
        assertThat(money.value()).isEqualTo("1000");
    }

    @Test
    void keepsAMultiTokenPlaceholderValueWhole() {
        RankRequirement placeholder =
                RankRequirement.parse("placeholder %player_level% >= 10").orElseThrow();
        assertThat(placeholder.type()).isEqualTo(RankRequirementType.PLACEHOLDER);
        assertThat(placeholder.value()).isEqualTo("%player_level% >= 10");
    }

    @Test
    void resolvesThePreviousRankAlias() {
        RankRequirement prev = RankRequirement.parse("prev-rank citizen").orElseThrow();
        assertThat(prev.type()).isEqualTo(RankRequirementType.PREVIOUS_RANK);
        assertThat(prev.value()).isEqualTo("citizen");
    }

    @Test
    void skipsAKeywordOnlyOrUnknownEntry() {
        assertThat(RankRequirement.parse("money")).isEmpty();
        assertThat(RankRequirement.parse("gibberish 5")).isEmpty();
        assertThat(RankRequirement.parse("   ")).isEmpty();
    }
}
