package com.uxplima.uxmessentials.velocity;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import com.uxplima.uxmessentials.shared.network.FreezeChanged;
import com.uxplima.uxmessentials.shared.network.FreezeReset;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.NetworkMessageCodec;
import org.jspecify.annotations.NullMarked;

/**
 * Which players are frozen, and on which server, as the freeze frames the broker relays tell it. A freeze is held in
 * memory by every backend, but a backend nobody was on when the freeze was announced never heard it, so the proxy,
 * which sees every frame a backend sends, keeps a frozen player on the server they were connected to when frozen.
 *
 * <p>Only frames a backend sent reach this: the broker never reads a frame a client wrote. Changes are kept by the
 * time they were made, so a freeze and its release relayed out of order leave the later one. A release is remembered
 * for {@link #RELEASE_MEMORY} only, long enough for a freeze it overtook to arrive, so the memory holds the players
 * frozen now rather than everyone ever frozen.
 *
 * <p>A backend announces a {@link FreezeReset} when its moderation module starts, since a restart or a reload drops
 * every freeze it held; the freezes kept for that server from before then are forgotten. Memory only: a proxy restart
 * forgets every freeze.
 */
@NullMarked
final class ProxyFreezes {

    /** How long a release is remembered after the proxy saw it. */
    static final Duration RELEASE_MEMORY = Duration.ofMinutes(5);

    private final Map<UUID, Change> latest = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    /** Freezes as the broker relays them; {@code clock} is the proxy's time in epoch milliseconds. */
    ProxyFreezes(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Take note of {@code frame}, relayed from {@code sourceServer}, when it is a freeze, a release or a reset. */
    void observe(byte[] frame, String sourceServer) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(sourceServer, "sourceServer");
        boolean change = NetworkMessageCodec.isType(frame, NetworkMessage.MessageType.FREEZE_CHANGED);
        if (!change && !NetworkMessageCodec.isType(frame, NetworkMessage.MessageType.FREEZE_RESET)) {
            return;
        }
        NetworkMessage message;
        try {
            message = NetworkMessageCodec.decode(frame);
        } catch (IllegalArgumentException malformed) {
            // A body that does not read is no change to hold.
            return;
        }
        long now = clock.getAsLong();
        latest.values().removeIf(held -> !held.frozen() && now - held.seenAt() > RELEASE_MEMORY.toMillis());
        if (message instanceof FreezeChanged freeze) {
            // Kept on the server the player was connected to, which is the one that froze them unless staff froze
            // them from another backend.
            Change next = new Change(freeze.frozen(), freeze.heldOn(), freeze.changedAtMillis(), now);
            latest.merge(freeze.target(), next, (held, incoming) -> incoming.at() < held.at() ? held : incoming);
        } else if (message instanceof FreezeReset reset) {
            latest.values()
                    .removeIf(held ->
                            held.frozen() && held.server().equals(sourceServer) && held.at() < reset.clearedAtMillis());
        }
    }

    /** The server {@code player} was frozen on, or empty when they are not frozen. */
    Optional<String> frozenOn(UUID player) {
        Objects.requireNonNull(player, "player");
        Change change = latest.get(player);
        return change == null || !change.frozen() ? Optional.empty() : Optional.of(change.server());
    }

    /**
     * One player's last change.
     *
     * @param frozen whether it froze them
     * @param server the server whose frame carried it
     * @param at when the change was made, by that server's clock
     * @param seenAt when the proxy saw it, by the proxy's clock
     */
    private record Change(boolean frozen, String server, long at, long seenAt) {}
}
