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
 * backend has its own invite book and bus, and a test carries the frames between them by hand.
 */
class CrossServerInvitesTest {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final Duration COOLDOWN = Duration.ofSeconds(5);

    private final MutableClock clock = new MutableClock(NOW);
    private final Backend lobby = new Backend("lobby", 2);
    private final Backend survival = new Backend("survival", 2);
    private final Backend hub = new Backend("hub", 2);

    @Test
    void anInviteOpensNothingUntilTheTargetAccepts() {
        lobby.online(ALEX);
        survival.online(SAM);
        TradeId id = lobby.invites.invite(ALEX, "sam");

        TradeSignal invite = lobby.only(TradeSignalType.INVITE);
        assertThat(survival.invites.onInvite(invite)).hasValueSatisfying(held -> {
            assertThat(held.inviter()).isEqualTo(ALEX);
            assertThat(held.target()).isEqualTo(SAM);
        });
        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.DELIVERED)))
                .isEmpty();
        assertThat(survival.invites.pendingInviterNames(SAM.uuid())).containsExactly("Alex");

        CrossServerInvites.Answer answer = survival.invites.accept(SAM.uuid(), "alex");

        assertThat(answer.status()).isEqualTo(CrossServerInvites.Status.MATCHED);
        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.ACCEPT)))
                .contains(new CrossServerInvites.Accepted(id, ALEX, SAM, "survival"));
        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isEqualTo(5);
    }

    @Test
    void aDeniedInviteTellsTheInviterAndKeepsTheirCooldown() {
        lobby.online(ALEX);
        survival.online(SAM);
        lobby.invites.invite(ALEX, "Sam");
        survival.invites.onInvite(lobby.only(TradeSignalType.INVITE));
        lobby.invites.onReply(survival.only(TradeSignalType.DELIVERED));

        assertThat(survival.invites.deny(SAM.uuid(), null).status()).isEqualTo(CrossServerInvites.Status.MATCHED);

        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.DECLINE)))
                .contains(new CrossServerInvites.Denied(ALEX, SAM));
        assertThat(survival.invites.accept(SAM.uuid(), null).status()).isEqualTo(CrossServerInvites.Status.NONE);
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

        assertThat(survival.invites.onInvite(invite)).isEmpty();
        assertThat(hub.invites.onInvite(invite)).isEmpty();

        assertThat(lobby.invites.onReply(survival.only(TradeSignalType.NOT_FOUND)))
                .isEmpty();
        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isEqualTo(5);
        assertThat(lobby.invites.onReply(hub.only(TradeSignalType.NOT_FOUND)))
                .contains(new CrossServerInvites.NotFound(ALEX, "Sam"));
        assertThat(lobby.cooldown.remainingSeconds(ALEX.uuid())).isZero();
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
        lobby.online(ALEX);
        survival.online(SAM);
        lobby.invites.invite(ALEX, "Sam");
        survival.invites.onInvite(lobby.only(TradeSignalType.INVITE));
        lobby.invites.onReply(survival.only(TradeSignalType.DELIVERED));

        clock.advance(TTL);
        assertThat(lobby.invites.expire()).isEmpty();
        clock.advance(CrossServerInvites.ANSWER_GRACE);

        assertThat(lobby.invites.expire()).containsExactly(new CrossServerInvites.Expired(ALEX, "Sam"));
        survival.invites.onAbort(lobby.only(TradeSignalType.ABORT));
        assertThat(survival.invites.accept(SAM.uuid(), "Alex").status()).isEqualTo(CrossServerInvites.Status.NONE);
        assertThat(survival.sent).isEmpty();
    }

    @Test
    void anAcceptAfterTheTargetsExpiryAnswersNothing() {
        lobby.online(ALEX);
        survival.online(SAM);
        lobby.invites.invite(ALEX, "Sam");
        survival.invites.onInvite(lobby.only(TradeSignalType.INVITE));
        survival.sent.clear();

        clock.advance(TTL.plusSeconds(1));

        assertThat(survival.invites.accept(SAM.uuid(), null).status()).isEqualTo(CrossServerInvites.Status.EXPIRED);
        assertThat(survival.sent).isEmpty();
    }

    @Test
    void anAcceptForAnInviteNoLongerTrackedIsAnsweredWithAbort() {
        lobby.online(ALEX);
        survival.online(SAM);
        TradeId id = lobby.invites.invite(ALEX, "Sam");
        survival.invites.onInvite(lobby.only(TradeSignalType.INVITE));
        survival.sent.clear();
        clock.advance(TTL);
        lobby.invites.expire();

        TradeSignal lateAccept = new TradeSignal(id, TradeSignalType.ACCEPT, SAM, ALEX, "survival");

        assertThat(lobby.invites.onReply(lateAccept)).isEmpty();
        TradeSignal abort = lobby.only(TradeSignalType.ABORT);
        assertThat(abort.tradeId()).isEqualTo(id);
        assertThat(abort.to()).isEqualTo(SAM);
    }

    /** One backend: its own bus, players and invite book, sharing the test's clock. */
    private final class Backend {

        final String server;
        final List<TradeSignal> sent = new ArrayList<>();
        final Set<PlayerRef> players = new HashSet<>();
        final Set<UUID> trading = new HashSet<>();
        final TradeCooldown cooldown = new TradeCooldown(clock, COOLDOWN);
        final CrossServerInvites invites;

        Backend(String server, int livePeers) {
            this.server = server;
            this.invites =
                    new CrossServerInvites(new RecordingBus(), new Presence(), () -> livePeers, cooldown, clock, TTL);
        }

        void online(PlayerRef player) {
            players.add(player);
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
