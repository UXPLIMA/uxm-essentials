package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Private messages, their answers, help requests and staff chat cross the wire unchanged, the longest message fits in
 * one relayed frame, and a peer that does not know these frames refuses them without losing their origin.
 */
class MessagingFramesTest {

    private static final UUID ID = UUID.fromString("2f0e8c1c-6b7e-4f3a-9d2a-1d6a3c0b9e11");
    private static final UUID ALEX = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID BOB = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");

    @Test
    void aPrivateMessageCrossesTheWireUnchanged() {
        PrivateMessageFrame sent =
                new PrivateMessageFrame("survival-1", ID, "lobby-1", ALEX, "Alex", BOB, "Bob", "hi there", true);

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void anAnswerCrossesTheWireUnchanged() {
        PrivateMessageAnswerFrame sent = new PrivateMessageAnswerFrame(
                "lobby-1", ID, "DELIVERED", ALEX, "Alex", BOB, "Bob", "hi there", true, "at lunch");

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void aHelpRequestCrossesTheWireUnchanged() {
        HelpOpFrame sent = new HelpOpFrame("survival-1", ID, ALEX, "Alex", "stuck in a wall");

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void aStaffChatLineCrossesTheWireUnchanged() {
        StaffChatFrame sent = new StaffChatFrame("survival-1", ID, ALEX, "Alex", "watch the spawn");

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void theLongestMessageInThreeByteCharactersFitsOneRelayedFrame() {
        String body = "中".repeat(256);
        PrivateMessageAnswerFrame answer = new PrivateMessageAnswerFrame(
                "a-backend-with-a-long-name", ID, "DELIVERED", ALEX, "Spieler_ä123456", BOB, "Bob", body, true, body);

        assertThat(NetworkMessageCodec.encode(answer).length).isLessThanOrEqualTo(BusChannel.MAX_FRAME_BYTES);
    }

    @Test
    void aPeerThatDoesNotKnowTheseFramesRefusesThemButStillReadsTheirOrigin() {
        byte[] frame = NetworkMessageCodec.encode(new StaffChatFrame("survival-1", ID, ALEX, "Alex", "hello"));
        frame[1] = 99;

        assertThatThrownBy(() -> NetworkMessageCodec.decode(frame)).isInstanceOf(UnknownMessageTypeException.class);
        assertThat(NetworkMessageCodec.readOrigin(frame)).isEqualTo("survival-1");
    }

    private static NetworkMessage roundTrip(NetworkMessage sent) {
        return NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));
    }
}
