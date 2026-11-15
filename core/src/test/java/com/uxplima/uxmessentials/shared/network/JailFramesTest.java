package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A jail change and a failed jail hop announced on one server arrive intact on another. */
class JailFramesTest {

    private static final UUID FRAME = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000a1e");

    @Test
    void aJailAndAReleaseCrossTheWireUnchanged() {
        for (boolean jailed : new boolean[] {true, false}) {
            JailChanged sent = new JailChanged("lobby", FRAME, ALEX, jailed);

            assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent)))
                    .isEqualTo(sent);
        }
    }

    @Test
    void aFailedJailHopCrossesTheWireUnchanged() {
        JailHopFailed sent = new JailHopFailed("survival", FRAME, ALEX, "Alex", "cell");

        assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent))).isEqualTo(sent);
    }

    @Test
    void anOlderPeerFindsTheOriginOfAJailFrame() {
        byte[] wire = NetworkMessageCodec.encode(new JailChanged("lobby", FRAME, ALEX, true));

        assertThat(NetworkMessageCodec.readOrigin(wire)).isEqualTo("lobby");
    }
}
