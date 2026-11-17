package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A skin change announced on one server arrives intact on another. */
class PlayerSkinChangedTest {

    @Test
    void aSkinChangeCrossesTheWireUnchanged() {
        PlayerSkinChanged sent =
                new PlayerSkinChanged("survival-1", UUID.fromString("00000000-0000-0000-0000-0000000005c1"));

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }
}
