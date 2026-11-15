package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** An IP ban announced on one server arrives intact on another. */
class IpBanChangedTest {

    @Test
    void anIpBanCrossesTheWireUnchanged() {
        IpBanChanged sent = new IpBanChanged("survival-1", "2001:db8::7");

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }
}
