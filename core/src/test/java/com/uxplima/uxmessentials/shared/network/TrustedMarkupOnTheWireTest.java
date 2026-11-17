package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.TrustedMarkup;
import org.junit.jupiter.api.Test;

/**
 * The mark that lets a value into a line as markup only counts on the server run that made it, so it never crosses to
 * another server: a relayed frame carries the text without it.
 */
class TrustedMarkupOnTheWireTest {

    private static final String EDGE = String.valueOf((char) 0xFDD1);

    @Test
    void aRelayedLineArrivesWithoutTheMark() {
        StaffChatFrame sent = new StaffChatFrame(
                "lobby", UUID.randomUUID(), UUID.randomUUID(), "Alex", TrustedMarkup.of("<gold>hi</gold>"));

        byte[] frame = NetworkMessageCodec.encode(sent);
        NetworkMessage received = NetworkMessageCodec.decode(frame);

        assertThat(new String(frame, StandardCharsets.UTF_8)).doesNotContain(EDGE);
        assertThat(received)
                .isInstanceOfSatisfying(
                        StaffChatFrame.class, line -> assertThat(line.message()).isEqualTo("<gold>hi</gold>"));
    }
}
