package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A world change announced on one server arrives intact on another. */
class WorldChangedTest {

    @Test
    void aWorldChangeCrossesTheWireUnchanged() {
        WorldChanged sent = new WorldChanged(
                "survival", UUID.fromString("00000000-0000-0000-0000-0000000000aa"), "survival", "arena");

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
        assertThat(sent.type().wireTag()).isEqualTo((byte) 30);
    }
}
