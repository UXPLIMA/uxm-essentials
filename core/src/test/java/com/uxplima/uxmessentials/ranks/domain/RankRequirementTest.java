package com.uxplima.uxmessentials.ranks.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RankRequirementTest {

    @Test
    void playtimeAndActivePlaytimeAreTwoKinds() {
        assertThat(RankRequirement.parse("playtime 3600"))
                .contains(new RankRequirement(RankRequirementType.PLAYTIME, "3600"));
        assertThat(RankRequirement.parse("playtime-active 3600"))
                .contains(new RankRequirement(RankRequirementType.PLAYTIME_ACTIVE, "3600"));
    }
}
