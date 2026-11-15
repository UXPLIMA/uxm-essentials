package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.network.ServerPing;
import org.jspecify.annotations.NullMarked;

/**
 * Notices another backend announcing itself under this backend's {@code network.server-name}. The bus drops
 * every frame stamped with its own name so a write never echoes back, which also means two backends sharing a
 * name drop each other's frames and sync between them stops with nothing to show for it. Each boot draws a
 * random nonce and stamps it into its heartbeats; a ping with our name and a nonce that is not ours can only
 * come from such a second backend. Our own pings echoed back (Redis delivers to the publisher too) carry our
 * nonce and are ignored.
 *
 * <p>The ERROR is logged once per clashing boot, so a running conflict does not flood the log on every beat
 * while a peer that restarts into the same mistake is reported again. The doctor reads {@link #seen} against the
 * liveness window, so the failure clears once the other backend is renamed and its last ping ages out.
 */
@NullMarked
public final class DuplicateServerNames {

    private final String serverName;
    private final long ownNonce;
    private final long windowMillis;
    private final Logger log;
    private final ConcurrentHashMap<Long, Long> lastSeenByNonce = new ConcurrentHashMap<>();

    /**
     * Watch for other backends using {@code serverName}.
     *
     * @param serverName this backend's {@code network.server-name}
     * @param ownNonce the nonce this boot stamps into its own heartbeats
     * @param window how long a clash stays reported after its last ping
     * @param log where the clash is reported
     */
    public DuplicateServerNames(String serverName, long ownNonce, Duration window, Logger log) {
        this.serverName = Objects.requireNonNull(serverName, "serverName");
        Objects.requireNonNull(window, "window");
        this.log = Objects.requireNonNull(log, "log");
        if (ownNonce == ServerPing.NO_NONCE) {
            throw new IllegalArgumentException("a boot nonce must not be the no-nonce marker");
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive: " + window);
        }
        this.ownNonce = ownNonce;
        this.windowMillis = window.toMillis();
    }

    /** This backend's name, the one a clash is about. */
    public String serverName() {
        return serverName;
    }

    /**
     * Look at a heartbeat stamped with this backend's name before the bus drops it. A ping from a peer too old
     * to carry a nonce cannot be told apart from our own echo, so it is left alone.
     */
    public void inspect(ServerPing ping) {
        Objects.requireNonNull(ping, "ping");
        long nonce = ping.bootNonce();
        if (!serverName.equals(ping.originServer()) || nonce == ServerPing.NO_NONCE || nonce == ownNonce) {
            return;
        }
        Long previous = lastSeenByNonce.putIfAbsent(nonce, ping.epochMillis());
        if (previous != null) {
            // A late older ping must not pull the last-seen time back.
            lastSeenByNonce.merge(nonce, ping.epochMillis(), Math::max);
        } else {
            log.error(
                    "Two backends share the network name '{}', so each drops the other's sync frames and nothing"
                            + " syncs between them. Set a unique network.server-name in config.conf on one of them"
                            + " and restart it.",
                    serverName);
        }
    }

    /** Whether another backend with this name has pinged within the window as of {@code now}. */
    public boolean seen(long now) {
        for (long lastSeen : lastSeenByNonce.values()) {
            if (now - lastSeen <= windowMillis) {
                return true;
            }
        }
        return false;
    }
}
