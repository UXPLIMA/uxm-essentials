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
 * <p>Every entry remembers the backend that reported it. A player who leaves that backend is dropped when it says so,
 * and every entry of a backend that stops sending heartbeats or starts again is dropped too, so the view never keeps a
 * player nobody is hiding any more. A departure from a backend other than the one the entry came from is ignored: on
 * a server hop the destination may already have reported the player before the origin's departure arrives.
 *
 * <p>The name rides along so the list can render a hidden player who is not online on the reading backend. When
 * {@code cross-server} is off the module wires {@link #empty()}, so the reconcile and list paths stay inert.
 */
public interface NetworkVanishStore {

    /** Apply a change reported by peer {@code origin}: record the player vanished, or drop them on a reveal. */
    void apply(String origin, VanishSync change);

    /** Peer {@code origin} says {@code player} left it; drop them unless another backend reported them since. */
    void depart(String origin, UUID player);

    /** Peer {@code origin} started again, so every player it reported belongs to a run that is over. */
    void forgetServer(String origin);

    /** The network-wide use level {@code who} is vanished at, or empty when no live peer reports them vanished. */
    Optional<VanishLevel> levelOf(UUID who);

    /** The last-known name of a network-vanished {@code who}, for a roster render; empty when not tracked. */
    Optional<String> nameOf(UUID who);

    /** A point-in-time copy of every network-vanished player keyed by uuid, for the aggregated {@code /vanish list}. */
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
        public void apply(String origin, VanishSync change) {
            // Nothing to track with cross-server off.
        }

        @Override
        public void depart(String origin, UUID player) {
            // Nothing tracked, so nothing to drop.
        }

        @Override
        public void forgetServer(String origin) {
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
