package com.uxplima.uxmessentials.security.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.security.application.SessionCarry.Arrival;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.ProxyConnectionFrame;
import org.junit.jupiter.api.Test;

/**
 * A join decision learns how the player arrived from the proxy's word, whether it came before the decision asked or
 * after; with no word in time the arrival is unconfirmed, and with no proxy plugin there is nothing to wait for.
 */
class ProxyArrivalsTest {

    private static final UUID ADA = new UUID(0L, 7L);
    private static final UUID CONNECTION = new UUID(1L, 1L);

    private final Delays delays = new Delays();
    private boolean proxyAnswering = true;
    private final ProxyArrivals arrivals = new ProxyArrivals(delays, () -> proxyAnswering);

    @Test
    void aSwitchTheProxyAnnouncedBeforeTheDecisionAskedIsKnownAtOnce() {
        arrivals.onRemoteChange(frame(ProxyConnectionFrame.PROXY, true));

        assertThat(arrivals.arrival(ADA)).isCompletedWithValue(new Arrival.ProxySwitch(CONNECTION));
        assertThat(arrivals.connection(ADA)).contains(CONNECTION);
    }

    @Test
    void aLoginTheProxyAnnouncesWhileTheDecisionWaitsAnswersIt() {
        CompletableFuture<Arrival> arrival = arrivals.arrival(ADA);
        assertThat(arrival).isNotDone();

        arrivals.onRemoteChange(frame(ProxyConnectionFrame.PROXY, false));

        assertThat(arrival).isCompletedWithValue(new Arrival.ProxyLogin());
    }

    @Test
    void noWordInTimeIsUnconfirmedAndAFrameFromABackendIsNoWord() {
        CompletableFuture<Arrival> arrival = arrivals.arrival(ADA);
        arrivals.onRemoteChange(frame("survival", true));

        delays.runAll();

        assertThat(delays.asked).containsExactly(ProxyArrivals.WAIT);
        assertThat(arrival).isCompletedWithValue(new Arrival.Unconfirmed());
    }

    @Test
    void withoutTheProxyPluginThereIsNothingToWaitFor() {
        proxyAnswering = false;

        assertThat(arrivals.arrival(ADA)).isCompletedWithValue(new Arrival.NoProxy());
    }

    @Test
    void whatTheProxySaidAboutAVisitEndsWhenThePlayerLeaves() {
        arrivals.onRemoteChange(frame(ProxyConnectionFrame.PROXY, true));

        arrivals.left(ADA);

        assertThat(arrivals.connection(ADA)).isEmpty();
        assertThat(arrivals.arrival(ADA)).isNotDone();
    }

    private static ProxyConnectionFrame frame(String origin, boolean switched) {
        return new ProxyConnectionFrame(origin, UUID.randomUUID(), ADA, CONNECTION, "survival", switched);
    }

    /** Delayed async tasks, run when the test says; nothing else is used. */
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
            throw new AssertionError("only delayed tasks are used");
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new AssertionError("only delayed tasks are used");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new AssertionError("only delayed tasks are used");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new AssertionError("only delayed tasks are used");
        }
    }
}
