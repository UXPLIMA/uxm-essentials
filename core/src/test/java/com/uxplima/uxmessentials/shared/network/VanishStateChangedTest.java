package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A vanish frame says whether the player left the origin, and a frame from an older peer reads as a plain change. */
class VanishStateChangedTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000a1e");

    @Test
    void aDepartureCrossesTheWireAsADeparture() {
        VanishStateChanged sent = new VanishStateChanged("survival-1", ALEX, "Alex", true, 3, true);

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void aFrameFromAPeerWithoutTheFlagIsNotADeparture() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(NetworkMessageCodec.VERSION);
            out.writeByte(NetworkMessage.MessageType.VANISH_STATE_CHANGED.wireTag());
            out.writeUTF("survival-2");
            out.writeLong(ALEX.getMostSignificantBits());
            out.writeLong(ALEX.getLeastSignificantBits());
            out.writeUTF("Alex");
            out.writeBoolean(true);
            out.writeInt(2);
        }

        NetworkMessage received = NetworkMessageCodec.decode(bytes.toByteArray());

        assertThat(received).isEqualTo(new VanishStateChanged("survival-2", ALEX, "Alex", true, 2, false));
    }
}
