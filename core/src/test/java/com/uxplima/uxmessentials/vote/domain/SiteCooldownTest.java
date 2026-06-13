package com.uxplima.uxmessentials.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SiteCooldownTest {

    private static final Instant BASE = Instant.ofEpochSecond(1_000_000);
    private static final Duration TWELVE_HOURS = Duration.ofHours(12);

    @Test
    void noLastVoteIsAlwaysVotable() {
        SiteCooldown cd = new SiteCooldown("TopVoter", Optional.empty(), TWELVE_HOURS);
        assertThat(cd.isVotable(BASE)).isTrue();
        assertThat(cd.remaining(BASE)).isEqualTo(Duration.ZERO);
    }

    @Test
    void insideCooldownWindowIsNotVotableAndHasPositiveRemaining() {
        Instant votedAt = BASE;
        Instant now = votedAt.plus(Duration.ofHours(6)); // 6h into a 12h cooldown
        SiteCooldown cd = new SiteCooldown("TopVoter", Optional.of(votedAt), TWELVE_HOURS);

        assertThat(cd.isVotable(now)).isFalse();
        Duration remaining = cd.remaining(now);
        assertThat(remaining).isGreaterThan(Duration.ZERO);
        assertThat(remaining).isEqualTo(Duration.ofHours(6));
    }

    @Test
    void exactlyAtCooldownExpiryIsVotable() {
        Instant votedAt = BASE;
        Instant now = votedAt.plus(TWELVE_HOURS); // exactly at expiry
        SiteCooldown cd = new SiteCooldown("TopVoter", Optional.of(votedAt), TWELVE_HOURS);

        assertThat(cd.isVotable(now)).isTrue();
        assertThat(cd.remaining(now)).isEqualTo(Duration.ZERO);
    }

    @Test
    void afterCooldownExpiryIsVotableWithZeroRemaining() {
        Instant votedAt = BASE;
        Instant now = votedAt.plus(TWELVE_HOURS).plusSeconds(1);
        SiteCooldown cd = new SiteCooldown("TopVoter", Optional.of(votedAt), TWELVE_HOURS);

        assertThat(cd.isVotable(now)).isTrue();
        assertThat(cd.remaining(now)).isEqualTo(Duration.ZERO);
    }

    @Test
    void zeroCooldownIsAlwaysVotable() {
        SiteCooldown cd = new SiteCooldown("AlwaysOn", Optional.of(BASE), Duration.ZERO);
        assertThat(cd.isVotable(BASE)).isTrue();
        assertThat(cd.remaining(BASE)).isEqualTo(Duration.ZERO);
    }
}
