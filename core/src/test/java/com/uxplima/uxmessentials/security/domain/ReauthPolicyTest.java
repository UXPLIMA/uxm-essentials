package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import com.uxplima.uxmessentials.security.domain.ReauthPolicy.Decision;
import org.junit.jupiter.api.Test;

/** Pins {@link ReauthPolicy}: the protected-vs-not match (with slash/namespace/argument forms) and the window edge. */
class ReauthPolicyTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private static final ReauthPolicy POLICY = new ReauthPolicy(Set.of("op", "deop", "gamemode"), WINDOW);

    @Test
    void aCommandThatIsNotOnTheListIsAllowed() {
        assertThat(POLICY.isProtected("spawn")).isFalse();
        assertThat(POLICY.decide("spawn", null, NOW)).isEqualTo(Decision.ALLOW);
    }

    @Test
    void aProtectedCommandWithNoPriorVerificationRequiresReauth() {
        assertThat(POLICY.isProtected("op")).isTrue();
        assertThat(POLICY.decide("op", null, NOW)).isEqualTo(Decision.REQUIRE_REAUTH);
    }

    @Test
    void aProtectedCommandVerifiedInsideTheWindowIsAllowed() {
        Instant verified = NOW.minusSeconds(30);
        assertThat(POLICY.decide("op", verified, NOW)).isEqualTo(Decision.ALLOW);
    }

    @Test
    void aProtectedCommandVerifiedPastTheWindowRequiresReauth() {
        Instant verified = NOW.minusSeconds(61);
        assertThat(POLICY.decide("op", verified, NOW)).isEqualTo(Decision.REQUIRE_REAUTH);
    }

    @Test
    void exactlyAtTheWindowBoundaryIsStillAllowed() {
        Instant verified = NOW.minus(WINDOW);
        assertThat(POLICY.decide("op", verified, NOW)).isEqualTo(Decision.ALLOW);
    }

    @Test
    void matchingIgnoresLeadingSlashArgumentsNamespaceAndCase() {
        assertThat(POLICY.isProtected("/op Steve")).isTrue();
        assertThat(POLICY.isProtected("minecraft:gamemode")).isTrue();
        assertThat(POLICY.isProtected("/gamemode creative Steve")).isTrue();
        assertThat(POLICY.isProtected("OP")).isTrue();
        assertThat(POLICY.decide("/op Steve", null, NOW)).isEqualTo(Decision.REQUIRE_REAUTH);
    }

    @Test
    void anEmptyPolicyProtectsNothing() {
        ReauthPolicy empty = new ReauthPolicy(Set.of(), WINDOW);
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.decide("op", null, NOW)).isEqualTo(Decision.ALLOW);
    }

    @Test
    void configuredEntriesAreNormalisedSoSlashAndNamespaceFormsStillMatch() {
        ReauthPolicy policy = new ReauthPolicy(Set.of("/stop", "minecraft:ban"), WINDOW);
        assertThat(policy.isProtected("stop")).isTrue();
        assertThat(policy.isProtected("ban")).isTrue();
    }

    @Test
    void aNegativeWindowIsRejected() {
        assertThatThrownBy(() -> new ReauthPolicy(Set.of("op"), Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
