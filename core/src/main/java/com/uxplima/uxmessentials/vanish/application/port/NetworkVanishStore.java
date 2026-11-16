package com.uxplima.uxmessentials.vanish.application.port;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * The network-wide vanish view a backend keeps of who is vanished on its <em>peers</em>, separate from the local
 * {@link VanishStore} of who this backend is hiding. It is fed only by the cross-server bus and read by the join
 * reconcile and the network-wide {@code /vanish list}.
 *
 * <p>Every entry remembers the backend that reported it. When a player leaves that backend the entry turns into a
 * departure, which still hides the player if they arrive here within the hop window (on a server switch the origin's
 * departure can arrive before the destination's join) but no longer lists them. A departure from a backend other than
 * the one the entry came from is ignored. The entries of a backend that stops running, or of an earlier run of a
 * backend that started again, are dropped, so the view never keeps a player nobody is hiding any more.
 *
 * <p>The name rides along so the list can render a hidden player who is not online on the reading backend. When
 * {@code cross-server} is off the module wires {@link #empty()}, so the reconcile and list paths stay inert.
 */
public interface NetworkVanishStore {

    /**
     * The run of a peer that does not name its runs on vanish frames, the bus's no-nonce marker. It never counts as an
     * ended run: such a peer's frames all carry it, before and after a restart.
     */
    long UNKNOWN_RUN = 0L;

    /**
     * The sequence of a frame from a peer that does not number its vanish frames. Such a frame is never older than
     * another: it is applied in the order it arrives.
     */
    long UNKNOWN_SEQUENCE = 0L;

    /**
     * Apply a change reported by run {@code run} of peer {@code origin} as its frame number {@code sequence}: record
     * the player vanished, or drop them on a reveal. A change from a run that already ended is ignored, and so is one
     * no newer than the last change or departure of the same player that run reported.
     *
     * @return whether the change was applied; a caller acts on it only then
     */
    boolean apply(String origin, long run, long sequence, VanishSync change);

    /**
     * Peer {@code origin} says, in frame number {@code sequence} of its run {@code run}, that {@code player} left it;
     * their entry from that backend becomes a departure. A departure older than the last frame applied for that player
     * from the same run is ignored, and a newer one makes any older frame of the run that arrives later count as old.
     */
    void depart(String origin, long run, long sequence, UUID player);

    /**
     * Run {@code run} of peer {@code origin} sent a heartbeat. When it is the first of a new run ({@code newRun}), every
     * entry another run of that peer sent is dropped; what the new run already reported is kept.
     */
    void heartbeat(String origin, long run, boolean newRun);

    /**
     * The use level {@code who} is vanished at on a peer, or empty when no peer reports them vanished. A departure
     * within the hop window counts, since this read decides whether an arriving player is hidden.
     */
    Optional<VanishLevel> levelOf(UUID who);

    /** The last-known name of a network-vanished {@code who}, for a roster render; empty when not tracked. */
    Optional<String> nameOf(UUID who);

    /** Every player a peer is hiding right now keyed by uuid, for {@code /vanish list}; departures are left out. */
    Map<UUID, VanishLevel> levels();

    /** Drop every entry; called on module stop so a disable or reload leaves zero residual network state. */
    void clear();

    /** An always-empty view wired when {@code cross-server} is off, so the reconcile/list paths stay inert. */
    static NetworkVanishStore empty() {
        return Empty.INSTANCE;
    }

    /** The inert view: every change is dropped and every query is empty. */
    enum Empty implements NetworkVanishStore {
        INSTANCE;

        @Override
        public boolean apply(String origin, long run, long sequence, VanishSync change) {
            // Nothing to track with cross-server off, and nothing to order the change against.
            return true;
        }

        @Override
        public void depart(String origin, long run, long sequence, UUID player) {
            // Nothing tracked, so nothing to drop.
        }

        @Override
        public void heartbeat(String origin, long run, boolean newRun) {
            // Nothing tracked, so nothing to drop.
        }

        @Override
        public Optional<VanishLevel> levelOf(UUID who) {
            return Optional.empty();
        }

        @Override
        public Optional<String> nameOf(UUID who) {
            return Optional.empty();
        }

        @Override
        public Map<UUID, VanishLevel> levels() {
            return Map.of();
        }

        @Override
        public void clear() {
            // Nothing to clear.
        }
    }
}
