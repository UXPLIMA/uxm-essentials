package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.networkpresence.NetworkRoster;
import com.uxplima.uxmessentials.shared.application.networkpresence.TrackNetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.application.port.PlayerNameIndex;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.NameCase;
import org.jspecify.annotations.NullMarked;

/**
 * Builds network presence over the bus: the {@link NetworkRoster} every context reads, the {@link TrackNetworkPresence}
 * that keeps it current, and the loop that announces this backend's players with every heartbeat.
 *
 * <p>A disabled network still gets a roster and a tracker, so the local players are known and a context reads one
 * port either way; the publisher is the bus's no-op one and no announce loop runs.
 */
@NullMarked
public final class NetworkPresenceWiring {

    private NetworkPresenceWiring() {}

    /** Build presence for this backend and register its listener with the bus. */
    public static Wired wire(
            Bus bus,
            NetworkConfig network,
            PlayerNameIndex names,
            Supplier<NameCase> nameCase,
            Scheduler scheduler,
            Logger log) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(network, "network");
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        Clock clock = Clock.systemUTC();
        NetworkRoster roster =
                new NetworkRoster(network.serverId(), clock, network.peerLivenessWindow(), nameCase, names::shared);
        TrackNetworkPresence track =
                new TrackNetworkPresence(roster, NetworkPresenceSync.publisher(bus.publisher()), names, clock);
        bus.registry().register(NetworkPresenceSync.listener(track));
        Announcer announcer =
                network.enabled() ? new Announcer(track, scheduler, network.heartbeatInterval(), log) : null;
        return new Wired(roster, track, announcer, scheduler);
    }

    /** Built presence: the read port, the tracker the join listener feeds, and the announce loop's lifecycle. */
    public static final class Wired {

        private final NetworkRoster presence;
        private final TrackNetworkPresence track;
        private final @org.jspecify.annotations.Nullable Announcer announcer;
        private final Scheduler scheduler;

        private Wired(
                NetworkRoster presence,
                TrackNetworkPresence track,
                @org.jspecify.annotations.Nullable Announcer announcer,
                Scheduler scheduler) {
            this.presence = presence;
            this.track = track;
            this.announcer = announcer;
            this.scheduler = scheduler;
        }

        /** Which backend each online player is on. */
        public NetworkPresence presence() {
            return presence;
        }

        /** The tracker local joins and quits are fed into. */
        public TrackNetworkPresence track() {
            return track;
        }

        /**
         * Start announcing. Called right after the bus start, which itself waits for the first global tick: the hop
         * through the global queue puts the first announcement behind it, so it rides a started transport instead
         * of being discarded. A disabled network has nothing to announce to.
         */
        public void start() {
            Announcer loop = announcer;
            if (loop == null) {
                return;
            }
            scheduler.onGlobal(() -> scheduler.async(loop::start));
        }

        /** Stop announcing; the loop drops out on its next tick. Idempotent. */
        public void stop() {
            Announcer loop = announcer;
            if (loop != null) {
                loop.stop();
            }
        }
    }

    /**
     * Announces this backend's players once on start, asking the peers for theirs, and then with every heartbeat.
     * The same self-rescheduling shape as {@link ClusterHeartbeat}: {@link #stop()} flips a flag and the loop drops
     * out on its next tick. Announcing reads memory and queues a frame, so it runs on the async lane.
     */
    private static final class Announcer {

        private final TrackNetworkPresence track;
        private final Scheduler scheduler;
        private final Duration interval;
        private final Logger log;
        private volatile boolean running;
        private volatile boolean stopped;

        Announcer(TrackNetworkPresence track, Scheduler scheduler, Duration interval, Logger log) {
            this.track = track;
            this.scheduler = scheduler;
            this.interval = interval;
            this.log = log;
        }

        void start() {
            if (stopped || running) {
                return;
            }
            running = true;
            announce(true);
            scheduleNext();
        }

        void stop() {
            stopped = true;
            running = false;
        }

        private void scheduleNext() {
            if (running) {
                scheduler.asyncAfter(interval, this::tick);
            }
        }

        private void tick() {
            if (!running) {
                return;
            }
            announce(false);
            scheduleNext();
        }

        private void announce(boolean replyRequested) {
            try {
                track.announce(replyRequested);
            } catch (RuntimeException failure) {
                // A throwing announce must not end the loop, or peers would expire this backend's players.
                log.error("network presence announce failed", failure);
            }
        }
    }
}
