package com.uxplima.uxmessentials.moderation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The jail rules, with the online-only countdown as the headline case: a timed online-only sentence advances
 * only by the elapsed online duration passed to {@link JailState.Active#tickOnline}, never by wall-clock time
 * at the gate. A permanent sentence is always active; a wall-clock sentence expires at its instant.
 */
class JailStateTest {

    private static final Issuer STAFF = Issuer.console("admin");
    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");

    @Test
    void onlineTimedJailIsActiveUntilTheRemainderIsServed() {
        JailState.Active jail =
                (JailState.Active) JailState.onlineTimed("cells", Duration.ofMinutes(10), STAFF, none(), T0);

        // Wall-clock time at the gate never advances an online-only sentence: a year later it is still active.
        assertThat(jail.isActiveAt(T0.plus(Duration.ofDays(365)))).isTrue();
        assertThat(jail.isServed()).isFalse();
    }

    @Test
    void tickOnlineBurnsOnlineTimeOffTheRemainderAndReleasesAtZero() {
        JailState.Active jail =
                (JailState.Active) JailState.onlineTimed("cells", Duration.ofMinutes(10), STAFF, none(), T0);

        JailState.Active afterFour = jail.tickOnline(Duration.ofMinutes(4));
        assertThat(afterFour.remaining()).contains(Duration.ofMinutes(6));
        assertThat(afterFour.isServed()).isFalse();

        JailState.Active served = afterFour.tickOnline(Duration.ofMinutes(6));
        assertThat(served.remaining()).contains(Duration.ZERO);
        assertThat(served.isServed()).isTrue();
        assertThat(served.isActiveAt(T0)).isFalse();
    }

    @Test
    void tickOnlineNeverDrivesTheRemainderNegative() {
        JailState.Active jail =
                (JailState.Active) JailState.onlineTimed("cells", Duration.ofMinutes(2), STAFF, none(), T0);

        JailState.Active overshot = jail.tickOnline(Duration.ofMinutes(5));
        assertThat(overshot.remaining()).contains(Duration.ZERO);
        assertThat(overshot.isServed()).isTrue();
    }

    @Test
    void permanentJailIsAlwaysActiveAndNeverTicks() {
        JailState.Active jail = (JailState.Active) JailState.permanent("cells", STAFF, none(), T0);

        assertThat(jail.isPermanent()).isTrue();
        assertThat(jail.isOnlineTimed()).isFalse();
        assertThat(jail.isActiveAt(T0.plus(Duration.ofDays(3650)))).isTrue();
        assertThat(jail.tickOnline(Duration.ofHours(1))).isEqualTo(jail);
    }

    @Test
    void wallClockJailExpiresAtItsInstantRegardlessOfOnlineTime() {
        Instant until = T0.plus(Duration.ofHours(1));
        JailState.Active jail = (JailState.Active) JailState.wallClockTimed("cells", until, STAFF, none(), T0);

        assertThat(jail.isOnlineTimed()).isFalse();
        assertThat(jail.isActiveAt(until.minusSeconds(1))).isTrue();
        assertThat(jail.isActiveAt(until.plusSeconds(1))).isFalse();
        // A wall-clock sentence does not tick on online time.
        assertThat(jail.tickOnline(Duration.ofHours(2))).isEqualTo(jail);
    }

    @Test
    void noneIsNeverActive() {
        assertThat(JailState.none().isActiveAt(T0)).isFalse();
    }

    private static Optional<String> none() {
        return Optional.empty();
    }
}
