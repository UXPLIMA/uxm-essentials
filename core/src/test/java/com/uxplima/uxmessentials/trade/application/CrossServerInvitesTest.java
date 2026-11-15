package com.uxplima.uxmessentials.trade.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.application.port.TradePresence;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.junit.jupiter.api.Test;

/**
 * Alex on the lobby backend invites Sam on the survival backend; a third backend, the hub, hosts neither. Each
 * backend has its own invite book and bus, and a test carries the frames between them by hand, which is also how it
 * repeats or reorders them the way a doubled or racing transport would.
 */
class CrossServerInvitesTest {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration COOLDOWN = Duration.ofSeconds(5);

    private final MutableClock clock = new MutableClock(NOW);
    private final Backend lobby = new Backend("lobby", Set.of("survival", "hub"));
    private final Backend survival = new Backend("survival", Set.of("lobby", "hub"));
    private final Backend hub = new Backend("hub", Set.of("lobby", "survival"));

    @Test
    void theTargetsWindowWaitsUntilTheInviterConfirmsItsOwnSideIsOpen() {
        TradeId id = deliveredInvite();

        CrossServerInvites.Answer answer = survival.accept();

        assertThat(answer.status()).isEqualTo(CrossServerInvites.Status.MATCHED);
        Optional<CrossServerInvites.Reply> reply = lobby.invites.onReply(survival.only(TradeSignalType.ACCEPT));
        CrossServerInvites.Accepted accepted = new CrossServerInvites.Accepted(id, ALEX, SAM, "survival");
        assertThat(reply).contains(accepted);
        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isEqualTo(5);

        lobby.invites.opened(accepted);

        assertThat(survival.invites.onOpened(lobby.only(TradeSignalType.OPENED)))
                .hasValueSatisfying(invite -> assertThat(invite.inviter()).isEqualTo(ALEX));
        clock.advance(CrossServerInvites.ANSWER_GRACE);
        assertThat(survival.invites.expireAccepts()).isEmpty();
    }

    @Test
    void theTargetGivesTheTradeUpWhenTheInviterNeverOpensItsSide() {
        deliveredInvite();
        survival.accept();
        TradeSignal accept = survival.only(TradeSignalType.ACCEPT);

        clock.advance(CrossServerInvites.ANSWER_GRACE);

        assertThat(survival.invites.expireAccepts())
                .singleElement()
                .satisfies(invite -> assertThat(invite.inviter()).isEqualTo(ALEX));
        assertThat(survival.only(TradeSignalType.ABORT).to()).isEqualTo(ALEX);
        TradeSignal lateOpened = new TradeSignal(accept.tradeId(), TradeSignalType.OPENED, ALEX, SAM, "lobby");
        assertThat(survival.invites.onOpened(lateOpened)).isEmpty();
    }

    @Test
    void aDeniedInviteTellsTheInviterAndKeepsTheirCooldown() {
        deliveredInvite();

        assertThat(survival.invites.deny(SAM.uuid(), null).status()).isEqualTo(CrossServerInvites.Status.MATCHED);

        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.DECLINE)))
                .contains(new CrossServerInvites.Denied(ALEX, SAM));
        assertThat(survival.accept().status()).isEqualTo(CrossServerInvites.Status.NONE);
        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isEqualTo(5);
    }

    @Test
    void aTargetAlreadyTradingAnswersBusyAndTheCooldownIsGivenBack() {
        lobby.online(ALEX);
        survival.online(SAM);
        survival.trading.add(SAM.uuid());
        lobby.invites.invite(ALEX, "Sam");

        assertThat(survival.invites.onInvite(lobby.only(TradeSignalType.INVITE)))
                .isEmpty();

        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.BUSY)))
                .contains(new CrossServerInvites.Busy(ALEX, SAM));
        assertThat(survival.invites.pendingInviterNames(SAM.uuid())).isEmpty();
        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isZero();
    }

    @Test
    void notFoundIsReportedOnlyOnceEveryLivePeerHasSaidSo() {
        lobby.online(ALEX);
        lobby.invites.invite(ALEX, "Sam");
        TradeSignal invite = lobby.only(TradeSignalType.INVITE);
        survival.invites.onInvite(invite);
        hub.invites.onInvite(invite);

        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.NOT_FOUND)))
                .isEmpty();
        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isEqualTo(5);
        assertThat(lobby.invites.onReply(hub.only(TradeSignalType.NOT_FOUND)))
                .contains(new CrossServerInvites.NotFound(ALEX, "Sam"));
        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isZero();
    }

    @Test
    void aNotFoundTheBusDeliveredTwiceCountsAsOneAnswer() {
        lobby.online(ALEX);
        lobby.invites.invite(ALEX, "Sam");
        survival.invites.onInvite(lobby.only(TradeSignalType.INVITE));
        TradeSignal notFound = survival.only(TradeSignalType.NOT_FOUND);

        assertThat(lobby.invites.onReply(notFound)).isEmpty();
        assertThat(lobby.invites.onReply(notFound)).isEmpty();

        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isEqualTo(5);
    }

    @Test
    void anEmptyRosterNeverConcludesNotFoundBeforeTheExpiry() {
        lobby.live = Set.of();
        lobby.online(ALEX);
        lobby.invites.invite(ALEX, "Sam");
        survival.invites.onInvite(lobby.only(TradeSignalType.INVITE));

        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.NOT_FOUND)))
                .isEmpty();

        clock.advance(TTL);
        assertThat(lobby.invites.expire()).containsExactly(new CrossServerInvites.NotFound(ALEX, "Sam"));
    }

    @Test
    void anInviteNobodyAnswersTimesOutAtTheExpiryAndGivesTheCooldownBack() {
        lobby.online(ALEX);
        lobby.invites.invite(ALEX, "Sam");

        clock.advance(TTL.minusSeconds(1));
        assertThat(lobby.invites.expire()).isEmpty();
        clock.advance(Duration.ofSeconds(1));

        assertThat(lobby.invites.expire()).containsExactly(new CrossServerInvites.TimedOut(ALEX, "Sam"));
        assertThat(lobby.invites.expire()).isEmpty();
        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isZero();
    }

    @Test
    void aDeliveredInviteLeftUnansweredExpiresAndIsWithdrawnFromTheTarget() {
        deliveredInvite();

        clock.advance(TTL);
        assertThat(lobby.invites.expire()).isEmpty();
        clock.advance(CrossServerInvites.ANSWER_GRACE);

        assertThat(lobby.invites.expire()).containsExactly(new CrossServerInvites.Expired(ALEX, "Sam"));
        survival.invites.onAbort(lobby.only(TradeSignalType.ABORT));
        assertThat(survival.accept().status()).isEqualTo(CrossServerInvites.Status.NONE);
        assertThat(survival.sent).isEmpty();
    }

    @Test
    void anAcceptAfterTheTargetsExpiryAnswersNothing() {
        deliveredInvite();

        clock.advance(TTL.plusSeconds(1));

        assertThat(survival.accept().status()).isEqualTo(CrossServerInvites.Status.EXPIRED);
        assertThat(survival.sent).isEmpty();
    }

    @Test
    void aTargetWhoseSideCannotBeClaimedKeepsTheInviteAndSendsNothing() {
        deliveredInvite();

        CrossServerInvites.Answer answer = survival.invites.accept(SAM.uuid(), null, invite -> false);

        assertThat(answer.status()).isEqualTo(CrossServerInvites.Status.TRADING);
        assertThat(survival.sent).isEmpty();
        assertThat(survival.invites.pendingInviterNames(SAM.uuid())).containsExactly("Alex");
    }

    @Test
    void anAcceptTheBusDeliveredTwiceOpensOnceAndWithdrawsNothing() {
        TradeId id = deliveredInvite();
        survival.accept();
        TradeSignal accept = survival.only(TradeSignalType.ACCEPT);

        assertThat(lobby.invites.onReply(accept)).contains(new CrossServerInvites.Accepted(id, ALEX, SAM, "survival"));
        assertThat(lobby.invites.onReply(accept)).isEmpty();
        assertThat(lobby.invites.onReply(new TradeSignal(id, TradeSignalType.DELIVERED, SAM, ALEX, "survival")))
                .isEmpty();

        assertThat(lobby.sent).isEmpty();
    }

    @Test
    void anInviteRepeatedByTheBusIsShownAndAnsweredOnce() {
        lobby.online(ALEX);
        survival.online(SAM);
        lobby.invites.invite(ALEX, "Sam");
        TradeSignal invite = lobby.only(TradeSignalType.INVITE);

        assertThat(survival.invites.onInvite(invite)).isPresent();
        assertThat(survival.invites.onInvite(invite)).isEmpty();

        survival.only(TradeSignalType.DELIVERED);
    }

    @Test
    void anAcceptOvertakenByItsAbortOpensNothing() {
        TradeId id = deliveredInvite();
        survival.accept();
        TradeSignal accept = survival.only(TradeSignalType.ACCEPT);

        lobby.invites.onAbort(new TradeSignal(id, TradeSignalType.ABORT, SAM, ALEX, "survival"));

        assertThat(lobby.invites.onReply(accept)).isEmpty();
        assertThat(lobby.only(TradeSignalType.ABORT).to()).isEqualTo(SAM);
    }

    @Test
    void anInviterInAnotherTradeWhenTheAcceptArrivesIsToldAndTheTargetReleased() {
        deliveredInvite();
        survival.accept();
        lobby.trading.add(ALEX.uuid());

        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.ACCEPT)))
                .contains(new CrossServerInvites.AcceptedWhileBusy(ALEX, SAM));
        assertThat(survival.invites.onAbort(lobby.only(TradeSignalType.ABORT))).isPresent();
    }

    @Test
    void anInviterWhoLeavesBeforeDeliveryHasTheLateDeliveryAndAcceptWithdrawn() {
        lobby.online(ALEX);
        survival.online(SAM);
        TradeId id = lobby.invites.invite(ALEX, "Sam");
        survival.invites.onInvite(lobby.only(TradeSignalType.INVITE));
        lobby.invites.forget(ALEX.uuid());
        lobby.players.remove(ALEX);

        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.DELIVERED)))
                .isEmpty();
        assertThat(lobby.only(TradeSignalType.ABORT).tradeId()).isEqualTo(id);

        survival.accept();
        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.ACCEPT))).isEmpty();
        assertThat(lobby.only(TradeSignalType.ABORT).to()).isEqualTo(SAM);
    }

    @Test
    void anAbortForAnotherTradeLeavesTheWaitingInviteAlone() {
        TradeId id = deliveredInvite();

        survival.invites.onAbort(new TradeSignal(TradeId.newId(), TradeSignalType.ABORT, ALEX, SAM, "lobby"));
        assertThat(survival.invites.pendingInviterNames(SAM.uuid())).containsExactly("Alex");

        survival.invites.onAbort(new TradeSignal(id, TradeSignalType.ABORT, ALEX, SAM, "lobby"));
        assertThat(survival.invites.pendingInviterNames(SAM.uuid())).isEmpty();
    }

    /** Alex invites Sam and the survival backend delivers it; every frame so far has been read. */
    private TradeId deliveredInvite() {
        lobby.online(ALEX);
        survival.online(SAM);
        TradeId id = lobby.invites.invite(ALEX, "Sam");
        survival.invites.onInvite(lobby.only(TradeSignalType.INVITE));
        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.DELIVERED)))
                .isEmpty();
        return id;
    }

    /** One backend: its own bus, players, live roster and invite book, sharing the test's clock. */
    private final class Backend {

        final String server;
        final List<TradeSignal> sent = new ArrayList<>();
        final Set<PlayerRef> players = new HashSet<>();
        final Set<UUID> trading = new HashSet<>();
        final TradeCooldown cooldown = new TradeCooldown(clock, COOLDOWN);
        final CrossServerInvites invites;
        Set<String> live;

        Backend(String server, Set<String> live) {
            this.server = server;
            this.live = live;
            this.invites =
                    new CrossServerInvites(new RecordingBus(), new Presence(), () -> this.live, cooldown, clock, TTL);
        }

        void online(PlayerRef player) {
            players.add(player);
        }

        /** Sam accepts the newest invite, with their side free to claim. */
        CrossServerInvites.Answer accept() {
            return invites.accept(SAM.uuid(), null, invite -> true);
        }

        /** The one signal this backend sent since the last read, which must be of {@code type}. */
        TradeSignal only(TradeSignalType type) {
            assertThat(sent).as("signals sent by %s", server).hasSize(1);
            TradeSignal signal = sent.remove(0);
            assertThat(signal.type()).isEqualTo(type);
            return signal;
        }

        private final class RecordingBus implements TradeBus {

            @Override
            public void send(TradeSignal signal) {
                sent.add(signal);
            }

            @Override
            public void subscribe(Consumer<TradeSignal> handler) {}

            @Override
            public String localServer() {
                return server;
            }

            @Override
            public boolean healthy() {
                return true;
            }
        }

        private final class Presence implements TradePresence {

            @Override
            public Optional<PlayerRef> onlineByName(String name) {
                return players.stream()
                        .filter(player -> player.name().equalsIgnoreCase(name))
                        .findFirst();
            }

            @Override
            public boolean isOnline(UUID player) {
                return players.stream().anyMatch(online -> online.uuid().equals(player));
            }

            @Override
            public boolean isTrading(UUID player) {
                return trading.contains(player);
            }
        }
    }
}
