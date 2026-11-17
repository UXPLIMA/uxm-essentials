package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A change to one player's shared settings, announced on one server, arrives intact on another. */
class PlayerSettingsChangedTest {

    @Test
    void aSettingsChangeCrossesTheWireUnchanged() {
        PlayerSettingsChanged sent =
                new PlayerSettingsChanged("survival", UUID.fromString("00000000-0000-0000-0000-0000000000a1"));

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
        assertThat(sent.type().wireTag()).isEqualTo((byte) 37);
    }
}
