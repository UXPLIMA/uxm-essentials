package com.uxplima.uxmessentials.vanish.application.port;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * The vanish context's narrow view of the cross-server bus: it publishes local vanish changes and departures to the
 * peer backends and delivers what the peers send to a {@link Listener}, without the vanish application ever naming the
 * shared network frame types or the transport. The adapter bridges this to the shared {@code BusPublisher} /
 * {@code RemoteSyncRegistry}.
 *
 * <p>The bus is fire-and-forget and lossy by design (a peer may miss a frame during a restart). What a lost frame
 * costs is bounded: the durable {@link VanishRecords} decide whether a joining player is hidden, a peer that starts
 * again is sent every vanished player afresh, and the entries of a peer that stops are dropped once its heartbeats
 * stop. When the network bus is disabled the module wires {@link #disabled()}, so cross-server is inert.
 */
public interface VanishBus {

    /** Publish a local vanish change to the peer backends; the origin backend never receives its own frame. */
    void publish(VanishSync change);

    /**
     * Tell the peers that {@code who}, vanished at {@code level}, left this backend. They stay vanished; only this
     * backend stops hiding them, so the peers drop the entry this backend reported.
     */
    void publishDeparture(PlayerRef who, VanishLevel level);

    /** Register the listener that receives everything the peer backends send. */
    void subscribe(Listener listener);

    /** Whether the bus is currently able to move frames. Informational: publish is safe regardless. */
    boolean healthy();

    /** What a peer backend can tell this one. Called on the bus dispatch thread, never on a region thread. */
    interface Listener {

        /** Peer {@code origin} vanished, revealed or re-resolved a player. */
        void changed(String origin, VanishSync change);

        /** A vanished player left peer {@code origin}. */
        void departed(String origin, UUID player);

        /** This backend heard from peer {@code origin} for the first time; it may know nothing of this one yet. */
        void peerFound(String origin);

        /** Peer {@code origin} started again: what it reported belongs to its last run, and it knows nothing yet. */
        void peerStarted(String origin);
    }

    /** A no-op bus for a backend that opts out of cross-server vanish: publish drops, subscribe is never invoked. */
    static VanishBus disabled() {
        return Disabled.INSTANCE;
    }

    /** The inert bus wired when {@code cross-server} is off; every seam is a harmless no-op. */
    enum Disabled implements VanishBus {
        INSTANCE;

        @Override
        public void publish(VanishSync change) {
            // No peers to notify with cross-server off.
        }

        @Override
        public void publishDeparture(PlayerRef who, VanishLevel level) {
            // No peers to notify with cross-server off.
        }

        @Override
        public void subscribe(Listener listener) {
            // No frames ever arrive, so the listener is never stored.
        }

        @Override
        public boolean healthy() {
            return false;
        }
    }
}
