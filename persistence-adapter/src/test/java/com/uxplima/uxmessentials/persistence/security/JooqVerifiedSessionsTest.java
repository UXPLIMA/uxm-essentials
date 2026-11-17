package com.uxplima.uxmessentials.persistence.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.testing.EveryBackend;
import com.uxplima.uxmessentials.persistence.testing.TestDatabase;
import com.uxplima.uxmessentials.security.application.SessionCarry;
import com.uxplima.uxmessentials.security.application.SessionCarry.Arrival;
import com.uxplima.uxmessentials.security.application.port.VerifiedSessions.Session;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The session record through the real migrations, as two backends on one database use it: a proof made on one is
 * carried across a confirmed switch to the other, the old backend's late release leaves it alone, and a new login or
 * an expired release carries nothing.
 */
@EveryBackend
class JooqVerifiedSessionsTest {

    private static final UUID ADA = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CONNECTION = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Optional<String> HOME = Optional.of("ip-hash-home");
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(30);

    private DSLContext dsl;
    private JooqVerifiedSessions lobbyStore;
    private SessionCarry lobby;
    private SessionCarry survival;

    @BeforeEach
    void open(TestDatabase database) {
        dsl = database.dsl();
        lobbyStore = new JooqVerifiedSessions(dsl);
        lobby = new SessionCarry(lobbyStore, GRACE);
        survival = new SessionCarry(new JooqVerifiedSessions(database.dsl()), GRACE);
    }

    @Test
    void aConfirmedSwitchCarriesTheProofAndTheOldBackendsLateReleaseLeavesItAlone() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW);

        assertThat(survival.carries(ADA, "survival", new Arrival.ProxySwitch(CONNECTION), HOME, NOW, server -> true))
                .isTrue();
        lobby.left(ADA, "lobby", NOW);

        assertThat(lobbyStore.find(ADA)).hasValueSatisfying(session -> {
            assertThat(session.heldBy()).isEqualTo("survival");
            assertThat(session.connection()).contains(CONNECTION);
            assertThat(session.ipHash()).isEqualTo(HOME);
            assertThat(session.expiresAt()).isEmpty();
        });
    }

    @Test
    void aNewLoginOrAReleaseThatRanOutCarriesNothing() {
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW);
        assertThat(survival.carries(ADA, "survival", new Arrival.ProxyLogin(), HOME, NOW, server -> true))
                .isFalse();

        lobby.left(ADA, "lobby", NOW);

        assertThat(survival.carries(
                        ADA, "survival", new Arrival.ProxySwitch(CONNECTION), HOME, NOW.plus(GRACE), server -> true))
                .isFalse();
        Session seen = lobbyStore.find(ADA).orElseThrow();
        assertThat(lobbyStore.take(ADA, "survival", seen, NOW.plus(GRACE))).isFalse();
    }

    @Test
    void aTakeOfASessionThatChangedSinceItWasReadTakesNothing() {
        lobby.proved(ADA, "lobby", Optional.empty(), HOME, NOW);
        Session seen = lobbyStore.find(ADA).orElseThrow();
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), HOME, NOW);

        assertThat(lobbyStore.take(ADA, "survival", seen, NOW))
                .as("another connection")
                .isFalse();

        Session now = lobbyStore.find(ADA).orElseThrow();
        lobby.proved(ADA, "lobby", Optional.of(CONNECTION), Optional.of("ip-hash-elsewhere"), NOW);
        assertThat(lobbyStore.take(ADA, "survival", now, NOW))
                .as("another address")
                .isFalse();
        assertThat(lobbyStore.find(ADA).orElseThrow().heldBy()).isEqualTo("lobby");
    }

    @Test
    void aStartingBackendLeavesASessionItTookAfterItStarted() {
        lobby.proved(ADA, "lobby", Optional.empty(), HOME, NOW.plusSeconds(1));

        lobby.started("lobby", NOW);

        assertThat(lobbyStore.find(ADA).orElseThrow().expiresAt()).isEmpty();
    }

    @Test
    void theProxyOnceSeenStaysSeenForEveryBackend() {
        JooqVerifiedSessions survivalStore = new JooqVerifiedSessions(dsl);
        assertThat(survivalStore.proxySeen()).isFalse();

        lobbyStore.markProxySeen(NOW);
        lobbyStore.markProxySeen(NOW.plusSeconds(1));

        assertThat(survivalStore.proxySeen()).isTrue();
    }

    @Test
    void aStartedBackendReleasesWhatItHeldAndARevokeForgetsIt() {
        lobby.proved(ADA, "lobby", Optional.empty(), HOME, NOW.minusSeconds(1));

        lobby.started("lobby", NOW);
        assertThat(lobbyStore.find(ADA).orElseThrow().expiresAt()).contains(NOW);

        lobby.revoke(ADA);
        assertThat(lobbyStore.find(ADA)).isEmpty();
    }
}
