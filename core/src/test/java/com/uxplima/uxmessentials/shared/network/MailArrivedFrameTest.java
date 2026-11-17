package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A new-mail notice sent from one server arrives intact on another. */
class MailArrivedFrameTest {

    @Test
    void aNewMailNoticeCrossesTheWireUnchanged() {
        MailArrivedFrame sent = new MailArrivedFrame(
                "survival",
                UUID.fromString("00000000-0000-0000-0000-0000000000f1"),
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"),
                "Alex",
                "Sam");

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
        assertThat(sent.type().wireTag()).isEqualTo((byte) 38);
    }
}
