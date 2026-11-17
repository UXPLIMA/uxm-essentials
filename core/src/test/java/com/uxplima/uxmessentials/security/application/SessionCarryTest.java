package com.uxplima.uxmessentials.security.application;

import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.security.application.SessionCarry.Arrival;
import com.uxplima.uxmessentials.testing.MemoryVerifiedSessions;
import org.junit.jupiter.api.Test;

/**
 * Two backends sharing one session record: a proof made on one carries to the other across a switch, and never to a
 * new login. With the proxy plugin only a switch it confirms on the proof's connection carries; without it, the same
 * address within the grace does.
 */
class SessionCarryTest {

    private static final UUID ADA = new UUID(0L, 7L);
    private static final UUID CONNECTION = new UUID(1L, 1L);
    private static final Optional<String> HOME = Optional.of("ip-hash-home");
    private static final Duration GRACE = Duration.ofSeconds(30);
    private static final Predicate<String> BOTH_RUN = Set.of("lobby", "survival")::contains;

    private final MemoryVerifiedSessions shared = new MemoryVerifiedSessions();
    private final SessionCarry lobby = new SessionCarry(shared, GRACE);
    private final SessionCarry survival = new SessionCarry(shared, GRACE);

    @Test
    void aSwitchTheProxyConfirmsOnTheProofsConnectionCarriesEvenBeforeTheOldBackendSawThemLeave() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME);

        assertThat(survival.carries(ADA, "survival", new Arrival.ProxySwitch(CONNECTION), HOME, NOW, BOTH_RUN))
                .isTrue();
        lobby.left(ADA, "lobby", NOW);

        assertThat(shared.find(ADA).orElseThrow().heldBy()).isEqualTo("survival");
        assertThat(shared.find(ADA).orElseThrow().expiresAt())
                .as("the old backend's release does not touch a session the new one took")
                .isEmpty();
    }

    @Test
    void aSwitchOnAnotherConnectionANewLoginOrNoWordFromTheProxyAsksAgain() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME);

        assertThat(survival.carries(ADA, "survival", new Arrival.ProxySwitch(new UUID(1L, 2L)), HOME, NOW, BOTH_RUN))
                .isFalse();
        assertThat(survival.carries(ADA, "survival", new Arrival.ProxyLogin(), HOME, NOW, BOTH_RUN))
                .isFalse();
        assertThat(survival.carries(ADA, "survival", new Arrival.Unconfirmed(), HOME, NOW, BOTH_RUN))
                .isFalse();
    }

    @Test
    void withoutTheProxyTheSameAddressCarriesWithinTheGraceAndNotAfter() {
        lobby.proved(ADA, "lobby", Optional.empty(), HOME);
        lobby.left(ADA, "lobby", NOW);

        assertThat(survival.carries(ADA, "survival", new Arrival.NoProxy(), HOME, NOW.plus(GRACE), BOTH_RUN))
                .as("the grace has run out")
                .isFalse();
        assertThat(survival.carries(
                        ADA, "survival", new Arrival.NoProxy(), Optional.of("ip-hash-elsewhere"), NOW, BOTH_RUN))
                .as("another address")
                .isFalse();
        assertThat(survival.carries(ADA, "survival", new Arrival.NoProxy(), HOME, NOW.plusSeconds(29), BOTH_RUN))
                .isTrue();
    }

    @Test
    void withoutTheProxyASessionStillHeldByABackendThatStoppedDoesNotCarry() {
        lobby.proved(ADA, "lobby", Optional.empty(), HOME);

        assertThat(survival.carries(ADA, "survival", new Arrival.NoProxy(), HOME, NOW, Set.of("survival")::contains))
                .isFalse();
        assertThat(survival.carries(ADA, "survival", new Arrival.NoProxy(), HOME, NOW, BOTH_RUN))
                .as("the switch ordering: the old backend still has them")
                .isTrue();
    }

    @Test
    void aBackendThatStartsReleasesWhatItHeldWhenItStopped() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME);

        lobby.started("lobby", NOW);

        assertThat(survival.carries(ADA, "survival", new Arrival.ProxySwitch(CONNECTION), HOME, NOW, BOTH_RUN))
                .isFalse();
    }

    @Test
    void aRevokedSessionAndAZeroGraceCarryNothing() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME);
        lobby.revoke(ADA);

        assertThat(survival.carries(ADA, "survival", new Arrival.ProxySwitch(CONNECTION), HOME, NOW, BOTH_RUN))
                .isFalse();

        SessionCarry off = new SessionCarry(shared, Duration.ZERO);
        off.proved(ADA, "lobby", Optional.of(CONNECTION), HOME);
        assertThat(shared.find(ADA)).as("nothing is recorded with carrying off").isEmpty();
        assertThat(off.carries(ADA, "survival", new Arrival.ProxySwitch(CONNECTION), HOME, NOW, BOTH_RUN))
                .isFalse();
    }
}
