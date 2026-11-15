package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * A server-presence heartbeat. Each enabled backend's {@code ClusterHeartbeat} publishes one on a bounded
 * interval ({@code network.heartbeat-seconds}); a peer receiving it records the origin as a live cluster
 * member with a fresh last-seen timestamp in its {@code ClusterPeers} roster. That roster backs the
 * cluster-peer count and last-activity line in {@code /uxmess doctor}, a peer
 * count of zero on a multi-backend cluster means the proxy broker is missing or the bus channel names diverged.
 *
 * <p>The boot nonce tells two backends that announce the same name apart. The bus drops every frame stamped
 * with its own name, so a second backend left on the same {@code network.server-name} would otherwise vanish
 * without a trace; a ping carrying our name and a nonce that is not ours is that second backend.
 *
 * @param originServer the backend announcing its presence
 * @param epochMillis the origin's wall-clock send time, for last-seen bookkeeping and skew diagnostics
 * @param bootNonce a random number the origin drew when it started, or {@link #NO_NONCE} from a peer that
 *     predates it
 */
public record ServerPing(String originServer, long epochMillis, long bootNonce) implements NetworkMessage {

    /** The nonce of a ping from a peer whose frames carry none; it never matches or clashes with any boot. */
    public static final long NO_NONCE = 0L;

    public ServerPing {
        Objects.requireNonNull(originServer, "originServer");
    }

    @Override
    public MessageType type() {
        return MessageType.SERVER_PING;
    }
}
