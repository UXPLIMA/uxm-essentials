package com.uxplima.uxmessentials.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class VoteTallyTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    // A Wednesday.
    private static final Instant WEDNESDAY = Instant.parse("2026-10-28T12:00:00Z");

    @Test
    void aBucketCountsOnlyInTheWindowItWasWrittenIn() {
        VoteTally tally = VoteTally.empty().recordVote(WEDNESDAY, UTC).recordVote(WEDNESDAY, UTC);

        assertThat(tally.countIn(VotePeriod.DAILY, WEDNESDAY, UTC)).isEqualTo(2);
        assertThat(tally.countIn(VotePeriod.WEEKLY, WEDNESDAY, UTC)).isEqualTo(2);

        Instant thursday = WEDNESDAY.plusSeconds(86_400);
        assertThat(tally.countIn(VotePeriod.DAILY, thursday, UTC)).isZero();
        assertThat(tally.countIn(VotePeriod.WEEKLY, thursday, UTC)).isEqualTo(2);
        assertThat(tally.countIn(VotePeriod.MONTHLY, thursday, UTC)).isEqualTo(2);

        Instant nextMonth = Instant.parse("2026-11-02T12:00:00Z");
        assertThat(tally.countIn(VotePeriod.WEEKLY, nextMonth, UTC)).isZero();
        assertThat(tally.countIn(VotePeriod.MONTHLY, nextMonth, UTC)).isZero();
        assertThat(tally.countIn(VotePeriod.ALLTIME, nextMonth, UTC)).isEqualTo(2);
    }
}
