package com.uxplima.uxmessentials.velocity;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.network.FreezeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.jspecify.annotations.NullMarked;

/**
 * Which players are frozen, and on which server, as the freeze frames the broker relays tell it. A freeze is held in
 * memory by every backend, but a backend nobody was on when the freeze was announced never heard it, so the proxy,
 * which sees every frame a backend sends, keeps a frozen player on the server that froze them.
 *
 * <p>Only frames a backend sent reach this: the broker never reads a frame a client wrote. Changes are kept by the
 * time they were made, so a freeze and its release relayed out of order leave the later one. Memory only: a proxy
 * restart forgets every freeze, as a backend restart forgets its own.
 */
@NullMarked
final class ProxyFreezes {

    private final Map<UUID, Change> latest = new ConcurrentHashMap<>();

    /** Take note of {@code frame}, relayed from {@code sourceServer}, when it is a freeze or a release. */
    void observe(byte[] frame, String sourceServer) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(sourceServer, "sourceServer");
        if (!NetworkMessageCodec.isType(frame, NetworkMessage.MessageType.FREEZE_CHANGED)) {
            return;
        }
        FreezeChanged change;
        try {
            change = (FreezeChanged) NetworkMessageCodec.decode(frame);
        } catch (IllegalArgumentException malformed) {
            // A body that does not read is no change to hold.
            return;
        }
        Change next = new Change(change.frozen(), sourceServer, change.changedAtMillis());
        latest.merge(change.target(), next, (held, incoming) -> incoming.at() < held.at() ? held : incoming);
    }

    /** The server {@code player} was frozen on, or empty when they are not frozen. */
    Optional<String> frozenOn(UUID player) {
        Objects.requireNonNull(player, "player");
        Change change = latest.get(player);
        return change == null || !change.frozen() ? Optional.empty() : Optional.of(change.server());
    }

    private record Change(boolean frozen, String server, long at) {}
}
