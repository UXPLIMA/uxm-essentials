package com.uxplima.uxmessentials.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The mute rules: a permanent mute always gags, a timed mute gags only until its expiry instant, and the
 * not-muted state never gags. These are the questions the messaging {@code MutePolicy} asks.
 */
class MuteStateTest {

    private static final Issuer STAFF = Issuer.console("admin");
    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");

    @Test
    void permanentMuteIsAlwaysActive() {
        MuteState mute = MuteState.permanent(STAFF, Optional.of("spam"), T0);

        assertThat(mute.isActiveAt(T0)).isTrue();
        assertThat(mute.isActiveAt(T0.plus(Duration.ofDays(3650)))).isTrue();
        assertThat(mute.expiry()).isEmpty();
    }

    @Test
    void timedMuteGagsOnlyUntilItsExpiry() {
        Instant until = T0.plus(Duration.ofHours(2));
        MuteState mute = MuteState.timed(until, STAFF, Optional.empty(), T0);

        assertThat(mute.isActiveAt(T0)).isTrue();
        assertThat(mute.isActiveAt(until.minusSeconds(1))).isTrue();
        assertThat(mute.isActiveAt(until)).isFalse();
        assertThat(mute.isActiveAt(until.plusSeconds(1))).isFalse();
        assertThat(mute.expiry()).contains(until);
    }

    @Test
    void noneIsNeverActive() {
        assertThat(MuteState.none().isActiveAt(T0)).isFalse();
        assertThat(MuteState.none().expiry()).isEmpty();
    }
}
