package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * A vanish frame says whether the player left the origin, which run sent it and where it stands among that run's
 * frames; an older peer's frame reads as a plain change.
 */
class VanishStateChangedTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000a1e");

    @Test
    void aDepartureCrossesTheWireWithItsRunAndSequence() {
        VanishStateChanged sent = new VanishStateChanged("survival-1", ALEX, "Alex", true, 3, true, 0x5eed_cafeL, 42L);

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void aFrameFromAPeerWithoutTheTrailingFieldsIsAChangeFromNoNamedRun() throws IOException {
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

        assertThat(received)
                .isEqualTo(new VanishStateChanged(
                        "survival-2",
                        ALEX,
                        "Alex",
                        true,
                        2,
                        false,
                        ServerPing.NO_NONCE,
                        VanishStateChanged.NO_SEQUENCE));
    }

    @Test
    void aFrameWithARunButNoSequenceIsAppliedInArrivalOrder() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(NetworkMessageCodec.VERSION);
            out.writeByte(NetworkMessage.MessageType.VANISH_STATE_CHANGED.wireTag());
            out.writeUTF("survival-2");
            out.writeLong(ALEX.getMostSignificantBits());
            out.writeLong(ALEX.getLeastSignificantBits());
            out.writeUTF("Alex");
            out.writeBoolean(false);
            out.writeInt(1);
            out.writeBoolean(false);
            out.writeLong(7L);
        }

        NetworkMessage received = NetworkMessageCodec.decode(bytes.toByteArray());

        assertThat(received)
                .isEqualTo(new VanishStateChanged(
                        "survival-2", ALEX, "Alex", false, 1, false, 7L, VanishStateChanged.NO_SEQUENCE));
    }
}
