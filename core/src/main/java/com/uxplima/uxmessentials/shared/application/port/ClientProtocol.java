package com.uxplima.uxmessentials.shared.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The network protocol version a player connected with, on servers where older clients are let in through a
 * translation layer.
 *
 * <p>Paper answers no such question: to the server every player is on the server's own protocol, because the
 * translation happens before the packets reach it. Only the translating plugin knows what the client at the
 * other end actually speaks, which is why every packet-based plugin that cares (TAB, UnlimitedNametags,
 * ZNPCsPlus) reads it from ViaVersion.
 *
 * <p>What this port is for is capability, not identity. A client several versions behind cannot render some
 * of what a current one can, and an operator who supports those clients needs a way to say "hide this from
 * them" rather than showing something broken. It is not a security check: a client's reported version is a
 * claim, and a hostile client can claim anything.
 *
 * <p>With no translation layer installed the binding is {@link #UNAVAILABLE} and every player answers
 * {@link #UNKNOWN}, which callers must treat as "no information" rather than "old".
 */
public interface ClientProtocol {

    /** No protocol version is known for this player: the answer on a server with no translation layer. */
    int UNKNOWN = -1;

    /** A source that knows nothing: every player answers {@link #UNKNOWN}. */
    ClientProtocol UNAVAILABLE = who -> UNKNOWN;

    /** The protocol version {@code who} connected with, or {@link #UNKNOWN} when it cannot be determined. */
    int protocolVersion(PlayerRef who);
}
