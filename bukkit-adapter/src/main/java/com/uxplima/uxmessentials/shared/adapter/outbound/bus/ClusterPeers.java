package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.jspecify.annotations.NullMarked;

/**
 * The live roster of peer backends, fed by the {@link ServerPing} heartbeats that arrive over the bus. Each
 * inbound ping records its origin's {@code server-id} against the wall-clock time it was seen; a peer is
 * "live" while its last ping is within {@link #livenessWindowMillis}, so a backend that crashed or lost the
 * bus ages out of the count without any explicit goodbye. This is what backs the cluster-peer line in
 * {@code /uxmess doctor}.
 *
 * <p>This backend never appears in its own roster: {@link BusCore} drops self-origin frames before dispatch,
 * so only genuine peers ever reach {@link #record}. The map is keyed by {@code server-id}, so a peer that
 * restarts simply refreshes its existing entry rather than accumulating duplicates.
 *
 * <p>Reads take {@code now} as an argument rather than calling the clock themselves, so the window logic is
 * unit-testable against an injected time; the doctor passes the real wall clock.
 */
@NullMarked
public final class ClusterPeers {

    private final ConcurrentHashMap<String, Long> lastSeenByOrigin = new ConcurrentHashMap<>();
    private final long livenessWindowMillis;

    /**
     * Build a roster whose peers age out after {@code livenessWindow}. The window should be a small multiple of
     * the heartbeat interval (a peer that misses two or three pings is considered gone) so a single dropped
     * frame does not flap the count.
     */
    public ClusterPeers(Duration livenessWindow) {
        Objects.requireNonNull(livenessWindow, "livenessWindow");
        if (livenessWindow.isNegative() || livenessWindow.isZero()) {
            throw new IllegalArgumentException("livenessWindow must be positive: " + livenessWindow);
        }
        this.livenessWindowMillis = livenessWindow.toMillis();
    }

    /** Record (or refresh) a peer's presence from the heartbeat it sent at {@code epochMillis}. */
    public void record(String origin, long epochMillis) {
        Objects.requireNonNull(origin, "origin");
        // A late-arriving older ping must not overwrite a fresher last-seen, so keep the larger timestamp.
        lastSeenByOrigin.merge(origin, epochMillis, Math::max);
    }

    /** The number of peers whose last heartbeat is within the liveness window as of {@code now}. */
    public int livePeerCount(long now) {
        int live = 0;
        for (long lastSeen : lastSeenByOrigin.values()) {
            if (now - lastSeen <= livenessWindowMillis) {
                live++;
            }
        }
        return live;
    }

    /** The most recent heartbeat seen from any peer, or empty when no peer has ever been heard from. */
    public Optional<Long> lastActivity() {
        long latest = Long.MIN_VALUE;
        for (long lastSeen : lastSeenByOrigin.values()) {
            latest = Math.max(latest, lastSeen);
        }
        return latest == Long.MIN_VALUE ? Optional.empty() : Optional.of(latest);
    }

    /** A {@link RemoteSyncListener} that records the origin of every inbound {@link ServerPing} into this roster. */
    public RemoteSyncListener listener() {
        return message -> {
            if (message instanceof ServerPing ping) {
                record(ping.originServer(), ping.epochMillis());
            }
        };
    }
}
