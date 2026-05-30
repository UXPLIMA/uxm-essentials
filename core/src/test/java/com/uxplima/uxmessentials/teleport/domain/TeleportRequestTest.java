package com.uxplima.uxmessentials.teleport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest.Transition;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestAccepted;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestCancelled;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestDenied;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequestExpired;
import com.uxplima.uxmessentials.teleport.domain.event.TeleportRequested;
import org.junit.jupiter.api.Test;

/**
 * The {@code tpa} handshake aggregate: its lifecycle transitions, the events each raises, the TTL expiry
 * rule, and the illegal-transition guards. The clock is an injected {@link Instant} so expiry is
 * deterministic and the domain never reads the wall clock.
 */
class TeleportRequestTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final Instant NOW = Instant.parse("2026-05-30T12:00:00Z");
    private static final Instant TTL = NOW.plus(Duration.ofSeconds(60));

    @Test
    void openRaisesRequestedAndStartsPending() {
        Transition opened = TeleportRequest.open(ALICE, BOB, RequestDirection.TO_TARGET, TTL);

        assertThat(opened.request().phase()).isEqualTo(RequestState.Phase.PENDING);
        assertThat(opened.request().requester()).isEqualTo(ALICE);
        assertThat(opened.request().target()).isEqualTo(BOB);
        assertThat(opened.event()).isInstanceOf(TeleportRequested.class);
    }

    @Test
    void openRejectsSelfRequest() {
        assertThatThrownBy(() -> TeleportRequest.open(ALICE, ALICE, RequestDirection.TO_TARGET, TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moverFollowsTheDirection() {
        TeleportRequest toTarget = TeleportRequest.open(ALICE, BOB, RequestDirection.TO_TARGET, TTL)
                .request();
        TeleportRequest toRequester = TeleportRequest.open(ALICE, BOB, RequestDirection.TO_REQUESTER, TTL)
                .request();

        assertThat(toTarget.mover()).isEqualTo(ALICE); // /tpa: requester moves to target
        assertThat(toTarget.anchor()).isEqualTo(BOB);
        assertThat(toRequester.mover()).isEqualTo(BOB); // /tpahere: target moves to requester
        assertThat(toRequester.anchor()).isEqualTo(ALICE);
    }

    @Test
    void acceptMovesToAcceptedAndRaisesAccepted() {
        TeleportRequest request = TeleportRequest.open(ALICE, BOB, RequestDirection.TO_TARGET, TTL)
                .request();

        Transition accepted = request.accept(NOW);

        assertThat(accepted.request().phase()).isEqualTo(RequestState.Phase.ACCEPTED);
        assertThat(accepted.event()).isInstanceOf(TeleportRequestAccepted.class);
    }

    @Test
    void denyRaisesDeniedAndCancelRaisesCancelled() {
        TeleportRequest request = TeleportRequest.open(ALICE, BOB, RequestDirection.TO_TARGET, TTL)
                .request();

        assertThat(request.deny().event()).isInstanceOf(TeleportRequestDenied.class);
        assertThat(request.cancel().event()).isInstanceOf(TeleportRequestCancelled.class);
    }

    @Test
    void hasExpiredIsFalseBeforeTtlAndTrueAtOrAfter() {
        TeleportRequest request = TeleportRequest.open(ALICE, BOB, RequestDirection.TO_TARGET, TTL)
                .request();

        assertThat(request.hasExpired(NOW)).isFalse();
        assertThat(request.hasExpired(TTL.minusSeconds(1))).isFalse();
        assertThat(request.hasExpired(TTL)).isTrue();
        assertThat(request.hasExpired(TTL.plusSeconds(1))).isTrue();
    }

    @Test
    void expireRaisesExpiredOnlyOnceTheTtlElapsed() {
        TeleportRequest request = TeleportRequest.open(ALICE, BOB, RequestDirection.TO_TARGET, TTL)
                .request();

        assertThatThrownBy(() -> request.expire(NOW)).isInstanceOf(IllegalStateException.class);

        Transition expired = request.expire(TTL);
        assertThat(expired.request().phase()).isEqualTo(RequestState.Phase.EXPIRED);
        assertThat(expired.event()).isInstanceOf(TeleportRequestExpired.class);
    }

    @Test
    void acceptingAnExpiredRequestIsRejected() {
        TeleportRequest request = TeleportRequest.open(ALICE, BOB, RequestDirection.TO_TARGET, TTL)
                .request();

        assertThatThrownBy(() -> request.accept(TTL.plusSeconds(5))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aResolvedRequestCannotTransitionAgain() {
        TeleportRequest denied = TeleportRequest.open(ALICE, BOB, RequestDirection.TO_TARGET, TTL)
                .request()
                .deny()
                .request();

        assertThatThrownBy(() -> denied.accept(NOW)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(denied::cancel).isInstanceOf(IllegalStateException.class);
    }
}
