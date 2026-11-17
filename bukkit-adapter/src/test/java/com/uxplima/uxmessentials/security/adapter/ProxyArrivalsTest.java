package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.security.application.SessionCarry;
import com.uxplima.uxmessentials.security.application.SessionCarry.Arrival;
import com.uxplima.uxmessentials.security.application.port.VerifiedSessions;
import com.uxplima.uxmessentials.shared.adapter.outbound.bus.CarrierReceipts;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A join decision learns how the player arrived from the proxy's word, whether it came before the decision asked or
 * after; with no word in time the arrival is unconfirmed. Only a word about this backend, carried by the player's own
 * connection during their current join, counts. Once the network has heard from the proxy, an arrival always waits
 * for it; only a network that never had it has nothing to wait for.
 */
class ProxyArrivalsTest {

    private static final UUID ADA = new UUID(0L, 7L);
    private static final UUID BOB = new UUID(0L, 8L);
    private static final UUID CONNECTION = new UUID(1L, 1L);
    private static final String HERE = "survival";

    private final Delays delays = new Delays();
    private final Flag shared = new Flag();
    private long nanos = 1_000L;
    private final CarrierReceipts receipts = new CarrierReceipts(() -> nanos);
    private boolean proxyAnswering = true;
    private final ProxyArrivals arrivals = new ProxyArrivals(
            delays,
            receipts,
            HERE,
            () -> proxyAnswering,
            new SessionCarry(shared, Duration.ofSeconds(30)),
            new Silent(),
            Clock.fixed(Instant.parse("2030-01-01T12:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void join() {
        arrivals.joined(ADA);
        nanos++;
    }

    @Test
    void aSwitchTheProxyAnnouncedBeforeTheDecisionAskedIsKnownAtOnce() {
        hear(frame(ProxyConnectionFrame.PROXY, HERE, true), ADA);

        assertThat(arrivals.arrival(ADA)).isCompletedWithValue(new Arrival.ProxySwitch(CONNECTION));
        assertThat(arrivals.connection(ADA)).contains(CONNECTION);
    }

    @Test
    void aLoginTheProxyAnnouncesWhileTheDecisionWaitsAnswersIt() {
        CompletableFuture<Arrival> arrival = arrivals.arrival(ADA);
        assertThat(arrival).isNotDone();

        hear(frame(ProxyConnectionFrame.PROXY, HERE, false), ADA);

        assertThat(arrival).isCompletedWithValue(new Arrival.ProxyLogin());
    }

    @Test
    void noWordInTimeIsUnconfirmedAndAFrameFromABackendIsNoWord() {
        CompletableFuture<Arrival> arrival = arrivals.arrival(ADA);
        hear(frame("lobby", HERE, true), ADA);

        delays.runAll();

        assertThat(delays.asked).containsExactly(ProxyArrivals.WAIT);
        assertThat(arrival).isCompletedWithValue(new Arrival.Unconfirmed());
    }

    @Test
    void aWordAboutAnotherBackendOrCarriedByAnotherPlayerIsNoWord() {
        CompletableFuture<Arrival> arrival = arrivals.arrival(ADA);

        hear(frame(ProxyConnectionFrame.PROXY, "lobby", true), ADA);
        hear(frame(ProxyConnectionFrame.PROXY, HERE, true), BOB);
        arrivals.onRemoteChange(frame(ProxyConnectionFrame.PROXY, HERE, true));
        delays.runAll();

        assertThat(arrival).isCompletedWithValue(new Arrival.Unconfirmed());
    }

    @Test
    void aWordFromAnEarlierVisitDoesNotSpeakForTheNextJoin() {
        ProxyConnectionFrame earlier = frame(ProxyConnectionFrame.PROXY, HERE, true);
        receipts.received(earlier.frameId(), ADA);
        nanos++;
        arrivals.left(ADA);
        arrivals.joined(ADA);
        nanos++;
        // The bus delivers the old word only now, after the new join began.
        arrivals.onRemoteChange(earlier);

        assertThat(arrivals.connection(ADA)).isEmpty();
        assertThat(arrivals.arrival(ADA)).isNotDone();
    }

    @Test
    void whatTheProxySaidAboutAVisitEndsWhenThePlayerLeavesOrJoinsAgain() {
        hear(frame(ProxyConnectionFrame.PROXY, HERE, true), ADA);

        arrivals.joined(ADA);

        assertThat(arrivals.connection(ADA)).isEmpty();
        assertThat(arrivals.arrival(ADA)).isNotDone();
    }

    @Test
    void aNetworkThatNeverHadTheProxyPluginHasNothingToWaitFor() {
        proxyAnswering = false;
        arrivals.load();

        assertThat(arrivals.arrival(ADA)).isCompletedWithValue(new Arrival.NoProxy());
    }

    @Test
    void untilTheSharedRecordIsReadTheProxyCountsAsSeen() {
        proxyAnswering = false;

        assertThat(arrivals.arrival(ADA)).isNotDone();
    }

    @Test
    void onceTheProxyWasHeardAnArrivalWaitsForItEvenWhileItIsSilent() {
        arrivals.load();
        hear(frame(ProxyConnectionFrame.PROXY, HERE, false), ADA);
        delays.runAll();
        proxyAnswering = false;

        assertThat(shared.seen).isTrue();
        ProxyArrivals restarted = new ProxyArrivals(
                delays,
                receipts,
                HERE,
                () -> false,
                new SessionCarry(shared, Duration.ofSeconds(30)),
                new Silent(),
                Clock.systemUTC());
        restarted.load();
        restarted.joined(BOB);

        assertThat(restarted.arrival(BOB)).isNotDone();
    }

    /** {@code frame} arrives over {@code carrier}'s connection now and reaches the listener. */
    private void hear(ProxyConnectionFrame frame, UUID carrier) {
        receipts.received(frame.frameId(), carrier);
        nanos++;
        arrivals.onRemoteChange(frame);
    }

    private static ProxyConnectionFrame frame(String origin, String server, boolean switched) {
        return new ProxyConnectionFrame(origin, UUID.randomUUID(), ADA, CONNECTION, server, switched);
    }

    /** The shared record, of which only the proxy flag is read here. */
    private static final class Flag implements VerifiedSessions {

        volatile boolean seen;

        @Override
        public boolean proxySeen() {
            return seen;
        }

        @Override
        public void markProxySeen(Instant at) {
            seen = true;
        }

        @Override
        public Optional<Session> find(UUID player) {
            throw new AssertionError("no session is read here");
        }

        @Override
        public void record(
                UUID player, String server, Optional<UUID> connection, Optional<String> ipHash, Instant now) {
            throw new AssertionError("no session is written here");
        }

        @Override
        public boolean take(UUID player, String server, Session seen, Instant now) {
            throw new AssertionError("no session is written here");
        }

        @Override
        public void release(UUID player, String server, Instant expiresAt) {
            throw new AssertionError("no session is written here");
        }

        @Override
        public void releaseAll(String server, Instant startedAt) {
            throw new AssertionError("no session is written here");
        }

        @Override
        public void revoke(UUID player) {
            throw new AssertionError("no session is written here");
        }
    }

    /** Async and delayed tasks, run when the test says; nothing else is used. */
    private static final class Delays implements Scheduler {

        final List<Duration> asked = new ArrayList<>();
        private final List<Runnable> tasks = new ArrayList<>();

        void runAll() {
            List<Runnable> now = new ArrayList<>(tasks);
            tasks.clear();
            now.forEach(Runnable::run);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            asked.add(delay);
            tasks.add(task);
        }

        @Override
        public void async(Runnable task) {
            tasks.add(task);
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new AssertionError("only async tasks are used");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new AssertionError("only async tasks are used");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new AssertionError("only async tasks are used");
        }
    }

    private static final class Silent implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
