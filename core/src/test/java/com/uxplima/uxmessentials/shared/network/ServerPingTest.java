package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

/** A heartbeat carries its boot nonce, and peers on either side of that change still understand each other. */
class ServerPingTest {

    @Test
    void aHeartbeatCrossesTheWireWithItsBootNonce() {
        ServerPing sent = new ServerPing("lobby-1", 1_700_000_000_000L, 0x5eed_cafe_f00dL);

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void aHeartbeatFromAPeerWithoutANonceDecodesWithNone() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(NetworkMessageCodec.VERSION);
            out.writeByte(NetworkMessage.MessageType.SERVER_PING.wireTag());
            out.writeUTF("survival-2");
            out.writeLong(1_700_000_000_000L);
        }

        NetworkMessage received = NetworkMessageCodec.decode(bytes.toByteArray());

        assertThat(received).isEqualTo(new ServerPing("survival-2", 1_700_000_000_000L, ServerPing.NO_NONCE));
    }

    @Test
    void theProxyTellsAHeartbeatFromOtherFramesByItsHeader() {
        byte[] ping = NetworkMessageCodec.encode(new ServerPing("lobby-1", 1_700_000_000_000L, 42L));
        byte[] home = NetworkMessageCodec.encode(new HomeChanged("lobby-1", new java.util.UUID(1L, 2L)));
        byte[] otherVersion = ping.clone();
        otherVersion[0] = (byte) (NetworkMessageCodec.VERSION + 1);

        assertThat(NetworkMessageCodec.isServerPing(ping)).isTrue();
        assertThat(NetworkMessageCodec.isServerPing(home)).isFalse();
        assertThat(NetworkMessageCodec.isServerPing(otherVersion)).isFalse();
        assertThat(NetworkMessageCodec.isServerPing(new byte[] {NetworkMessageCodec.VERSION}))
                .isFalse();
    }

    @Test
    void aPeerOnTheOlderLayoutStillReadsTheNameAndTimeOfANewHeartbeat() throws IOException {
        byte[] frame = NetworkMessageCodec.encode(new ServerPing("lobby-1", 1_700_000_000_000L, 42L));

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame))) {
            assertThat(in.readByte()).isEqualTo(NetworkMessageCodec.VERSION);
            assertThat(in.readByte()).isEqualTo(NetworkMessage.MessageType.SERVER_PING.wireTag());
            assertThat(in.readUTF()).isEqualTo("lobby-1");
            assertThat(in.readLong()).isEqualTo(1_700_000_000_000L);
        }
    }
}
