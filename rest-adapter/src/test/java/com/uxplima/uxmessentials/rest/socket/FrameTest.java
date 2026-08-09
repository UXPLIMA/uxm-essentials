package com.uxplima.uxmessentials.rest.socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FrameTest {

    @Test
    void aTextFrameFromAClientComesBackAsTheTextThatWentIn() throws IOException {
        Frame frame = read(Frames.clientText("{\"subscribe\":[\"*\"]}"));

        assertThat(frame.opcode()).isEqualTo(Frame.TEXT);
        assertThat(frame.text()).isEqualTo("{\"subscribe\":[\"*\"]}");
    }

    /** Text over 125 bytes uses the two-byte length, which is the first place a hand-written framer goes wrong. */
    @Test
    void aFrameLongerThanTheShortFormSurvivesTheRoundTrip() throws IOException {
        String long1 = "x".repeat(1000);

        assertThat(read(Frames.clientText(long1)).text()).isEqualTo(long1);
    }

    @Test
    void aServerFrameIsUnmaskedAndSaysItsOwnLength() {
        byte[] frame = Frame.text("hi");

        assertThat(frame[0] & 0xFF).isEqualTo(0x81);
        assertThat(frame[1] & 0x80).isZero();
        assertThat(frame[1] & 0x7F).isEqualTo(2);
        assertThat(new String(frame, 2, 2, StandardCharsets.UTF_8)).isEqualTo("hi");
    }

    @Test
    void aServerFrameOverTheShortFormSwitchesToTheTwoByteLength() {
        byte[] frame = Frame.text("y".repeat(200));

        assertThat(frame[1] & 0x7F).isEqualTo(126);
        assertThat(((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF)).isEqualTo(200);
    }

    /** The specification requires it, and a server that skips the check can be used to poison a proxy. */
    @Test
    void anUnmaskedFrameFromAClientIsAProtocolError() {
        assertThatExceptionOfType(Frame.ProtocolException.class)
                .isThrownBy(() -> read(Frame.text("unmasked")))
                .satisfies(broken -> assertThat(broken.closeCode()).isEqualTo(Frame.CLOSE_PROTOCOL_ERROR));
    }

    @Test
    void aFragmentedMessageIsRefusedRatherThanBuffered() {
        byte[] first = Frames.clientText("half");
        first[0] = 0x01; // text, and not the final fragment

        assertThatExceptionOfType(Frame.ProtocolException.class)
                .isThrownBy(() -> read(first))
                .satisfies(broken -> assertThat(broken.closeCode()).isEqualTo(Frame.CLOSE_UNSUPPORTED));
    }

    @Test
    void aFrameThatClaimsToBeEnormousIsRefusedBeforeAnythingIsAllocated() {
        byte[] head =
                new byte[] {(byte) 0x81, (byte) 0xFF, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0};

        assertThatExceptionOfType(Frame.ProtocolException.class)
                .isThrownBy(() -> read(head))
                .satisfies(broken -> assertThat(broken.closeCode()).isEqualTo(Frame.CLOSE_TOO_BIG));
    }

    @Test
    void aConnectionThatEndsBetweenFramesIsAnEndingRatherThanAnError() {
        assertThatExceptionOfType(EOFException.class).isThrownBy(() -> read(new byte[0]));
    }

    @Test
    void aCloseFrameCarriesItsCodeInTheFirstTwoBytes() {
        byte[] frame = Frame.close(Frame.CLOSE_GOING_AWAY, "bye");

        assertThat(frame[0] & 0xFF).isEqualTo(0x88);
        assertThat(((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF)).isEqualTo(Frame.CLOSE_GOING_AWAY);
    }

    private static Frame read(byte[] bytes) throws IOException {
        return Frame.read(new ByteArrayInputStream(bytes));
    }
}
