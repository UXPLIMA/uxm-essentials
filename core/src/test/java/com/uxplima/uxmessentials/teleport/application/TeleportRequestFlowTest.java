package com.uxplima.uxmessentials.teleport.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.testing.ClockCooldowns;
import com.uxplima.uxmessentials.testing.Fixtures;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A {@code /tpa} lives for its TTL on the clock: accepted in time it warms the mover up, and once the TTL has passed
 * it can only expire, telling both players once and moving nobody, even before the sweep has run.
 */
class TeleportRequestFlowTest {

    private static final Position SAMS_SPOT = Fixtures.at(100, 70, 100);
    private static final PlayerRef TAYLOR =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000007a"), "Taylor");

    private final MutableClock clock = new MutableClock(NOW);
    private boolean jailed;
    private boolean tagged;
    private final TeleportFakes.Requests requests = new TeleportFakes.Requests();
    private final TeleportFakes.Flags flags = new TeleportFakes.Flags();
    private final TeleportFakes.HeldWarmups warmups = new TeleportFakes.HeldWarmups();
    private final TeleportFakes.Hops hops = new TeleportFakes.Hops();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingEvents events = new RecordingEvents();
    private final TeleportSettings settings = new TeleportSettings(TeleportFakes.Config.of(
            "request-ttl-seconds", "60", "default-cooldown", "30", "cooldown-start-phase", "accept"));
    private final TeleportEngine engine = new TeleportEngine(
            new ClockCooldowns(clock),
            warmups,
            hops,
            notifier.notifier(),
            events,
            settings,
            who -> jailed,
            who -> tagged);
    private final RequestTeleport request =
            new RequestTeleport(requests, flags, notifier.notifier(), events, settings, who -> jailed, clock);
    private final AcceptTeleport accept = new AcceptTeleport(requests, engine, notifier.notifier(), events, clock);

    private void alexAsksSam() {
        assertThat(request.request(ALEX, SAM, RequestDirection.TO_TARGET).isOk())
                .isTrue();
    }

    /** What the adapter's sweep does each second: look at every request still held and expire the due ones. */
    private void sweep() {
        requests.all().forEach(accept::expireIfDue);
    }

    private boolean alexIsOnCooldown() {
        return engine.gateHop(ALEX, Destination.at(SAMS_SPOT), TeleportKind.HOME)
                .isErr();
    }

    @Test
    void anAcceptInTimeWarmsTheMoverUpAndMovesThemOnce() {
        alexAsksSam();
        clock.advance(Duration.ofSeconds(59));

        assertThat(accept.accept(SAM, SAMS_SPOT).isOk()).isTrue();
        warmups.finish();

        assertThat(hops.landed).containsExactly("Alex:REQUEST");
        assertThat(requests.all()).isEmpty();
        assertThat(accept.accept(SAM, SAMS_SPOT).asError()).contains(TeleportError.NO_PENDING_REQUEST);
    }

    @Test
    void underTheAcceptPhaseOnlyAnAcceptedRequestStartsTheRequestersCooldown() {
        alexAsksSam();
        assertThat(alexIsOnCooldown()).isFalse();

        accept.accept(SAM, SAMS_SPOT);

        assertThat(alexIsOnCooldown()).isTrue();
    }

    @Test
    void aDeniedRequestStartsNoCooldownAndMovesNobody() {
        alexAsksSam();

        assertThat(accept.deny(SAM).isOk()).isTrue();

        assertThat(alexIsOnCooldown()).isFalse();
        assertThat(warmups.pending()).isZero();
        assertThat(requests.all()).isEmpty();
    }

    @Test
    void theSweepExpiresARequestAtItsTtlAndTellsBothPlayersOnce() {
        alexAsksSam();

        clock.advance(Duration.ofSeconds(59));
        sweep();
        assertThat(requests.all()).hasSize(1);

        clock.advance(Duration.ofSeconds(1));
        sweep();
        sweep();

        assertThat(requests.all()).isEmpty();
        assertThat(notifier.sent())
                .filteredOn(line -> line.key().equals("teleport.tpa.expired"))
                .containsExactly(
                        new RecordingNotifier.Sent(ALEX, "teleport.tpa.expired", Map.of("player", "Sam")),
                        new RecordingNotifier.Sent(SAM, "teleport.tpa.expired", Map.of("player", "Alex")));
        assertThat(alexIsOnCooldown()).isFalse();
    }

    @Test
    void anAcceptAfterTheTtlButBeforeTheSweepExpiresTheRequestAndMovesNobody() {
        alexAsksSam();
        clock.advance(Duration.ofSeconds(60));

        assertThat(accept.accept(SAM, SAMS_SPOT).asError()).contains(TeleportError.NO_PENDING_REQUEST);

        assertThat(warmups.pending()).isZero();
        assertThat(requests.all()).isEmpty();
        assertThat(alexIsOnCooldown()).isFalse();
        assertThat(notifier.sent())
                .filteredOn(line -> line.key().equals("teleport.tpa.expired"))
                .containsExactly(
                        new RecordingNotifier.Sent(ALEX, "teleport.tpa.expired", Map.of("player", "Sam")),
                        new RecordingNotifier.Sent(SAM, "teleport.tpa.expired", Map.of("player", "Alex")));
    }

    @Test
    void anExpiredRequestDoesNotHideAFreshOneQueuedBehindIt() {
        alexAsksSam();
        clock.advance(Duration.ofSeconds(30));
        assertThat(request.request(TAYLOR, SAM, RequestDirection.TO_TARGET).isOk())
                .isTrue();
        clock.advance(Duration.ofSeconds(30));

        assertThat(accept.accept(SAM, SAMS_SPOT).isOk()).isTrue();
        warmups.finish();

        assertThat(hops.landed).containsExactly("Taylor:REQUEST");
    }

    @Test
    void aRequestToAPlayerWhoRefusesRequestsIsNeverStored() {
        flags.setAcceptsRequests(SAM, false);

        assertThat(request.request(ALEX, SAM, RequestDirection.TO_TARGET).asError())
                .contains(TeleportError.TARGET_TOGGLED_OFF);

        assertThat(requests.all()).isEmpty();
        assertThat(notifier.keys())
                .containsExactly(TeleportError.TARGET_TOGGLED_OFF.messageKey().key());
    }

    @Test
    void aBlockedOrJailedRequesterIsRefusedAndNothingIsStored() {
        flags.block(SAM, ALEX);
        assertThat(request.request(ALEX, SAM, RequestDirection.TO_TARGET).asError())
                .contains(TeleportError.REQUESTER_BLOCKED);

        flags.unblock(SAM, ALEX);
        jailed = true;
        assertThat(request.request(ALEX, SAM, RequestDirection.TO_TARGET).asError())
                .contains(TeleportError.JAILED);

        assertThat(requests.all()).isEmpty();
    }

    @Test
    void aMoverJailedAfterAskingIsKeptInPlaceWhenTheRequestIsAccepted() {
        alexAsksSam();
        jailed = true;

        assertThat(accept.accept(SAM, SAMS_SPOT).asError()).contains(TeleportError.JAILED);
        warmups.finish();

        assertThat(warmups.pending()).isZero();
        assertThat(hops.landed).isEmpty();
        assertThat(requests.all()).isEmpty();
        assertThat(notifier.sent()).contains(new RecordingNotifier.Sent(ALEX, "teleport.jailed", Map.of()));
        jailed = false;
        assertThat(alexIsOnCooldown()).isFalse();
    }

    @Test
    void aCombatTaggedMoverOfAHereRequestIsKeptInPlaceWhenTheyAccept() {
        assertThat(request.request(ALEX, SAM, RequestDirection.TO_REQUESTER).isOk())
                .isTrue();
        tagged = true;

        assertThat(accept.accept(SAM, Fixtures.at(0, 64, 0)).asError()).contains(TeleportError.COMBAT_TAGGED);
        warmups.finish();

        assertThat(hops.landed).isEmpty();
        assertThat(notifier.sent()).contains(new RecordingNotifier.Sent(SAM, "teleport.combat-tagged", Map.of()));
    }
}
