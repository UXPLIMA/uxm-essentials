package com.uxplima.uxmessentials.security.application;

import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.security.application.SessionCarry.Arrival;
import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;
import com.uxplima.uxmessentials.testing.MemoryVerifiedSessions;
import org.junit.jupiter.api.Test;

/**
 * Two backends sharing one session record: a proof made on one carries to the other across a switch, and never to a
 * new login. With the proxy plugin only a switch it confirms on the proof's connection carries; on a network that never
 * had it, the same address within the grace does.
 */
class SessionCarryTest {

    private static final UUID ADA = new UUID(0L, 7L);
    private static final UUID CONNECTION = new UUID(1L, 1L);
    private static final Optional<String> HOME = Optional.of("ip-hash-home");
    private static final Duration GRACE = Duration.ofSeconds(30);

    private final MemoryVerifiedSessions shared = new MemoryVerifiedSessions();
    private final SessionCarry lobby = new SessionCarry(shared, GRACE);
    private final SessionCarry survival = new SessionCarry(shared, GRACE);

    @Test
    void aSwitchTheProxyConfirmsOnTheProofsConnectionCarriesEvenBeforeTheOldBackendSawThemLeave() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW);

        assertThat(survival.carries(
                        ADA,
                        "survival",
                        new Arrival.ProxySwitch(CONNECTION),
                        HOME,
                        NOW,
                        SessionCarryTest::stillOnLobby))
                .isTrue();
        lobby.left(ADA, "lobby", NOW);

        assertThat(shared.find(ADA).orElseThrow().heldBy()).isEqualTo("survival");
        assertThat(shared.find(ADA).orElseThrow().expiresAt())
                .as("the old backend's release does not touch a session the new one took")
                .isEmpty();
    }

    @Test
    void aSwitchOnAnotherConnectionANewLoginOrNoWordFromTheProxyAsksAgain() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW);

        assertThat(survival.carries(
                        ADA,
                        "survival",
                        new Arrival.ProxySwitch(new UUID(1L, 2L)),
                        HOME,
                        NOW,
                        SessionCarryTest::stillOnLobby))
                .isFalse();
        assertThat(survival.carries(
                        ADA, "survival", new Arrival.ProxyLogin(), HOME, NOW, SessionCarryTest::stillOnLobby))
                .isFalse();
        assertThat(survival.carries(
                        ADA, "survival", new Arrival.Unconfirmed(), HOME, NOW, SessionCarryTest::stillOnLobby))
                .isFalse();
    }

    @Test
    void withoutTheProxyTheSameAddressCarriesWithinTheGraceAndNotAfter() {
        lobby.proved(ADA, "lobby", Optional.empty(), HOME, NOW);
        lobby.left(ADA, "lobby", NOW);

        assertThat(survival.carries(
                        ADA, "survival", new Arrival.NoProxy(), HOME, NOW.plus(GRACE), SessionCarryTest::stillOnLobby))
                .as("the grace has run out")
                .isFalse();
        assertThat(survival.carries(
                        ADA,
                        "survival",
                        new Arrival.NoProxy(),
                        Optional.of("ip-hash-elsewhere"),
                        NOW,
                        SessionCarryTest::stillOnLobby))
                .as("another address")
                .isFalse();
        assertThat(survival.carries(
                        ADA,
                        "survival",
                        new Arrival.NoProxy(),
                        HOME,
                        NOW.plusSeconds(29),
                        SessionCarryTest::stillOnLobby))
                .isTrue();
    }

    @Test
    void withoutTheProxyASessionNeverReleasedCarriesOnlyWhileThePlayerIsStillOnTheBackendHoldingIt() {
        lobby.proved(ADA, "lobby", Optional.empty(), HOME, NOW);

        assertThat(survival.carries(
                        ADA,
                        "survival",
                        new Arrival.NoProxy(),
                        HOME,
                        NOW.plus(GRACE.multipliedBy(10)),
                        SessionCarryTest::gone))
                .as("they left the lobby long ago and its release never landed")
                .isFalse();
        assertThat(survival.carries(ADA, "survival", new Arrival.NoProxy(), HOME, NOW, SessionCarryTest::stillOnLobby))
                .as("the switch ordering: the old backend still has them")
                .isTrue();
    }

    @Test
    void withoutTheProxyASessionNeverReleasedDoesNotCarryBackToTheBackendHoldingIt() {
        lobby.proved(ADA, "lobby", Optional.empty(), HOME, NOW);

        assertThat(lobby.carries(
                        ADA, "lobby", new Arrival.NoProxy(), HOME, NOW.plus(GRACE.multipliedBy(10)), server -> true))
                .as("the lobby lists them only because they just joined it again")
                .isFalse();
    }

    @Test
    void aProofMadeOverAProxyConnectionNeverCarriesOnTheAddressAlone() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW);
        lobby.left(ADA, "lobby", NOW);

        assertThat(survival.carries(ADA, "survival", new Arrival.NoProxy(), HOME, NOW, SessionCarryTest::stillOnLobby))
                .isFalse();
    }

    @Test
    void aSessionRecordedAnewBetweenTheReadAndTheTakeIsNotTaken() {
        lobby.proved(ADA, "lobby", Optional.empty(), HOME, NOW);
        VerifiedSessions.Session seen = shared.find(ADA).orElseThrow();
        lobby.proved(ADA, "lobby", Optional.empty(), Optional.of("ip-hash-elsewhere"), NOW);

        assertThat(shared.take(ADA, "survival", seen, NOW)).isFalse();
    }

    @Test
    void theProxyOnceHeardIsRememberedForEveryBackend() {
        assertThat(survival.proxySeen()).isFalse();

        lobby.proxyHeard(NOW);

        assertThat(survival.proxySeen()).isTrue();
    }

    @Test
    void aBackendThatStartsReleasesWhatItHeldWhenItStoppedAndKeepsWhatItTookSince() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW.minusSeconds(1));

        lobby.started("lobby", NOW);

        assertThat(survival.carries(
                        ADA,
                        "survival",
                        new Arrival.ProxySwitch(CONNECTION),
                        HOME,
                        NOW,
                        SessionCarryTest::stillOnLobby))
                .isFalse();

        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW.plusSeconds(1));
        lobby.started("lobby", NOW);
        assertThat(shared.find(ADA).orElseThrow().expiresAt()).isEmpty();
    }

    @Test
    void aRevokedSessionAndAZeroGraceCarryNothing() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW);
        lobby.revoke(ADA);

        assertThat(survival.carries(
                        ADA,
                        "survival",
                        new Arrival.ProxySwitch(CONNECTION),
                        HOME,
                        NOW,
                        SessionCarryTest::stillOnLobby))
                .isFalse();

        SessionCarry off = new SessionCarry(shared, Duration.ZERO);
        off.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW);
        assertThat(shared.find(ADA)).as("nothing is recorded with carrying off").isEmpty();
        assertThat(off.carries(
                        ADA,
                        "survival",
                        new Arrival.ProxySwitch(CONNECTION),
                        HOME,
                        NOW,
                        SessionCarryTest::stillOnLobby))
                .isFalse();
    }

    /** The player is still on the lobby as far as the arriving backend knows. */
    private static boolean stillOnLobby(String server) {
        return server.equals("lobby");
    }

    /** The player is on no backend the arriving one knows of. */
    private static boolean gone(String server) {
        return false;
    }
}
