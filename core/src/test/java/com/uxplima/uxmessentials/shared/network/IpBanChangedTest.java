package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** An IP ban announced on one server arrives intact on another, and a tag a reader does not know is refused. */
class IpBanChangedTest {

    @Test
    void anIpBanCrossesTheWireUnchanged() {
        IpBanChanged sent = new IpBanChanged("survival-1", "2001:db8::7");

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void aFrameWithAnUnknownTagIsRefusedSoTheReaderCanDropIt() {
        byte[] frame = NetworkMessageCodec.encode(new IpBanChanged("survival-1", "203.0.113.7"));
        frame[1] = 99;

        assertThatIllegalArgumentException().isThrownBy(() -> NetworkMessageCodec.decode(frame));
    }
}
