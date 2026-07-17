package com.uxplima.uxmessentials.servertweaks.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Pins the {@code minecraft:brand} payload encoding: a VarInt length prefix followed by the brand's UTF-8 bytes. The
 * short-brand case fits a single VarInt byte; the 200-character case proves the two-byte VarInt path so a long brand
 * is length-prefixed correctly rather than truncated.
 */
class BrandPayloadTest {

    @Test
    void encodesAShortBrandAsAOneByteLengthThenUtf8() {
        byte[] payload = BrandPayload.encode("uxmEssentials");

        assertThat(payload[0]).isEqualTo((byte) 13); // "uxmEssentials" is 13 bytes, a single VarInt byte
        assertThat(new String(payload, 1, payload.length - 1, StandardCharsets.UTF_8))
                .isEqualTo("uxmEssentials");
        assertThat(payload).hasSize(14);
    }

    @Test
    void encodesALengthOverAHundredAndTwentySevenAsATwoByteVarInt() {
        String brand = "a".repeat(200);

        byte[] payload = BrandPayload.encode(brand);

        // 200 = 0b1100_1000 -> low 7 bits 0x48 with the continuation bit set (0xC8), then the remaining 0x01.
        assertThat(payload[0]).isEqualTo((byte) 0xC8);
        assertThat(payload[1]).isEqualTo((byte) 0x01);
        assertThat(new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8))
                .isEqualTo(brand);
        assertThat(payload).hasSize(202);
    }
}
