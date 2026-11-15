package com.uxplima.uxmessentials.shared.network;

import java.util.Objects;

/**
 * An address was IP-banned on the origin backend ({@code /banip} or {@code /tempbanip}), so a peer kicks every
 * player it has connected from that address. The login check already refuses the address everywhere; this
 * frame only removes the players a peer had online before the ban. The ban itself (reason, expiry) is read
 * from the shared database on the peer, never carried on the wire.
 *
 * @param originServer the backend that issued the ban
 * @param ip the banned address literal, compared exactly as the login check compares it
 */
public record IpBanChanged(String originServer, String ip) implements NetworkMessage {

    public IpBanChanged {
        Objects.requireNonNull(originServer, "originServer");
        Objects.requireNonNull(ip, "ip");
        if (ip.isBlank()) {
            throw new IllegalArgumentException("banned ip must not be blank");
        }
    }

    @Override
    public MessageType type() {
        return MessageType.IP_BAN_CHANGED;
    }
}
