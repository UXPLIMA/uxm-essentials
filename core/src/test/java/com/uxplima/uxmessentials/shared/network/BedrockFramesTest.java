package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** What the proxy and a backend say about a Bedrock player and their forms arrives intact, under its own tags. */
class BedrockFramesTest {

    private static final UUID MIRA = new UUID(0L, 0x0009_01f2_a3b4_c5d6L);

    @Test
    void aBedrockPlayersPresenceCrossesTheWireUnchanged() {
        BedrockPresenceFrame sent = new BedrockPresenceFrame(
                ProxyConnectionFrame.PROXY,
                new UUID(1L, 2L),
                MIRA,
                "survival",
                "2535412345678901",
                "GOOGLE",
                "TOUCH",
                "POCKET",
                "1.21.90",
                "tr_TR");

        assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent))).isEqualTo(sent);
        assertThat(sent.type().wireTag()).isEqualTo((byte) 41);
    }

    @Test
    void aFormRequestAndItsAnswerCrossTheWireUnchanged() {
        BedrockFormRequestFrame request = new BedrockFormRequestFrame("survival", MIRA, 7L, "AQIABFNob3A=");
        BedrockFormAnswerFrame answer = new BedrockFormAnswerFrame(
                ProxyConnectionFrame.PROXY, new UUID(3L, 4L), "survival", MIRA, 7L, "AQIAAQADcGluAAQ0ODIx");

        assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(request)))
                .isEqualTo(request);
        assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(answer)))
                .isEqualTo(answer);
        assertThat(request.type().wireTag()).isEqualTo((byte) 42);
        assertThat(answer.type().wireTag()).isEqualTo((byte) 47);
    }

    @Test
    void anAnswerNeverPrintsWhatWasTyped() {
        BedrockFormAnswerFrame answer = new BedrockFormAnswerFrame(
                ProxyConnectionFrame.PROXY, new UUID(3L, 4L), "survival", MIRA, 7L, "AQIAAQADcGluAAQ0ODIx");

        assertThat(answer.toString()).contains("survival").doesNotContain("AQIAAQAD");
    }
}
