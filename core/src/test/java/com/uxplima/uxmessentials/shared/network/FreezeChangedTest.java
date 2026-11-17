package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A freeze or a release announced on one server arrives intact on another, and the proxy can pick it out. */
class FreezeChangedTest {

    private static final UUID FRAME = UUID.fromString("00000000-0000-0000-0000-0000000000f2");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000a1e");

    @Test
    void aFreezeAndAReleaseCrossTheWireUnchanged() {
        for (boolean frozen : new boolean[] {true, false}) {
            FreezeChanged sent = new FreezeChanged("lobby", FRAME, ALEX, "Alex", frozen, 1_763_300_000_000L);

            assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent)))
                    .isEqualTo(sent);
        }
    }

    @Test
    void theServerAFreezeIsHeldOnCrossesTheWire() {
        FreezeChanged sent = new FreezeChanged("lobby", FRAME, ALEX, "Alex", true, 1_763_300_000_000L, "survival");

        FreezeChanged received = (FreezeChanged) NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received.heldOn()).isEqualTo("survival");
        assertThat(received).isEqualTo(sent);
    }

    @Test
    void aResetCrossesTheWireUnchanged() {
        FreezeReset sent = new FreezeReset("lobby", 1_763_300_000_000L);

        assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent))).isEqualTo(sent);
    }

    @Test
    void theProxyFindsAFreezeFrameByItsHeader() {
        byte[] wire = NetworkMessageCodec.encode(new FreezeChanged("lobby", FRAME, ALEX, "Alex", true, 1L));

        assertThat(NetworkMessageCodec.isType(wire, NetworkMessage.MessageType.FREEZE_CHANGED))
                .isTrue();
        assertThat(NetworkMessageCodec.isType(wire, NetworkMessage.MessageType.JAIL_CHANGED))
                .isFalse();
        assertThat(NetworkMessageCodec.readOrigin(wire)).isEqualTo("lobby");
    }
}
