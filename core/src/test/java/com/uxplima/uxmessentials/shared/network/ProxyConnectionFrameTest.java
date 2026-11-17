package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** The proxy's word on how a player reached a backend arrives intact. */
class ProxyConnectionFrameTest {

    @Test
    void aSwitchOnAConnectionCrossesTheWireUnchanged() {
        ProxyConnectionFrame sent = new ProxyConnectionFrame(
                ProxyConnectionFrame.PROXY, new UUID(1L, 2L), new UUID(3L, 4L), new UUID(5L, 6L), "survival", true);

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
        assertThat(sent.type().wireTag()).isEqualTo((byte) 35);
    }
}
