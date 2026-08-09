package com.uxplima.uxmessentials.rest.socket;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.uxplima.uxmessentials.rest.http.HttpRequest;
import com.uxplima.uxmessentials.rest.http.HttpStatus;
import org.junit.jupiter.api.Test;

class HandshakeTest {

    /**
     * The worked example from RFC 6455 section 1.3.
     *
     * <p>Pinned to the specification's own numbers rather than to whatever this implementation happens to produce,
     * because a hash that is self-consistently wrong passes every test but no real client.
     */
    @Test
    void theAcceptValueIsTheOneTheSpecificationWorksThrough() {
        assertThat(Handshake.signatureOf("dGhlIHNhbXBsZSBub25jZQ==")).isEqualTo("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=");
    }

    @Test
    void aProperHandshakeIsAnsweredWithTheSwitchingProtocolsHead() {
        byte[] answer = Handshake.acceptFor(upgrade(Map.of(
                        "upgrade", "websocket",
                        "sec-websocket-version", "13",
                        "sec-websocket-key", "dGhlIHNhbXBsZSBub25jZQ==")))
                .orElseThrow();

        assertThat(new String(answer, StandardCharsets.ISO_8859_1))
                .startsWith("HTTP/1.1 101 Switching Protocols\r\n")
                .contains("Upgrade: websocket\r\n")
                .contains("Connection: Upgrade\r\n")
                .contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n")
                .endsWith("\r\n\r\n");
    }

    @Test
    void anOrdinaryRequestToTheStreamIsToldToUpgrade() {
        assertThat(Handshake.refusalFor(upgrade(Map.of())).orElseThrow().status())
                .isEqualTo(HttpStatus.UPGRADE_REQUIRED);
    }

    /** An old client is told which version to use, in the header the specification keeps for saying so. */
    @Test
    void aVersionThisDoesNotSpeakIsRefusedWithTheVersionItDoes() {
        var refusal = Handshake.refusalFor(upgrade(Map.of(
                        "upgrade", "websocket",
                        "sec-websocket-version", "8",
                        "sec-websocket-key", "dGhlIHNhbXBsZSBub25jZQ==")))
                .orElseThrow();

        assertThat(refusal.status()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(refusal.headers()).containsEntry("Sec-WebSocket-Version", "13");
    }

    @Test
    void aHandshakeWithNoKeyIsRefusedRatherThanAnswered() {
        assertThat(Handshake.refusalFor(upgrade(Map.of("upgrade", "websocket", "sec-websocket-version", "13"))))
                .isPresent();
        assertThat(Handshake.acceptFor(upgrade(Map.of("upgrade", "websocket", "sec-websocket-version", "13"))))
                .isEmpty();
    }

    private static HttpRequest upgrade(Map<String, String> headers) {
        return new HttpRequest("GET", "/api/v1/events", Map.of(), headers, "");
    }
}
