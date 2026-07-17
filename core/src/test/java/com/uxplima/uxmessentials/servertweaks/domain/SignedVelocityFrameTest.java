package com.uxplima.uxmessentials.servertweaks.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Pins the SignedVelocity wire decode against frames written exactly as the proxy writes them (modified-UTF-8
 * {@code writeUTF} fields): each result token maps to the right directive, {@code MODIFY} carries its replacement, and
 * a bad token or a truncated buffer is rejected rather than silently mishandled.
 */
class SignedVelocityFrameTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void decodesAnAllowedChatFrame() {
        SignedVelocityFrame frame = SignedVelocityFrame.decode(frame("CHAT_RESULT", "ALLOWED", null));

        assertThat(frame.player()).isEqualTo(PLAYER);
        assertThat(frame.source()).isEqualTo(SignedSource.CHAT);
        assertThat(frame.directive().cancelled()).isFalse();
        assertThat(frame.directive().modifiedMessage()).isEmpty();
    }

    @Test
    void decodesACancelledCommandFrame() {
        SignedVelocityFrame frame = SignedVelocityFrame.decode(frame("COMMAND_RESULT", "CANCEL", null));

        assertThat(frame.source()).isEqualTo(SignedSource.COMMAND);
        assertThat(frame.directive().cancelled()).isTrue();
    }

    @Test
    void decodesAModifyFrameWithItsReplacement() {
        SignedVelocityFrame frame = SignedVelocityFrame.decode(frame("CHAT_RESULT", "MODIFY", "cleaned message"));

        assertThat(frame.directive().cancelled()).isFalse();
        assertThat(frame.directive().modifiedMessage()).contains("cleaned message");
    }

    @Test
    void rejectsAnUnknownResultToken() {
        assertThatThrownBy(() -> SignedVelocityFrame.decode(frame("CHAT_RESULT", "WHATEVER", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAnUnknownSourceToken() {
        assertThatThrownBy(() -> SignedVelocityFrame.decode(frame("MYSTERY", "ALLOWED", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsATruncatedBuffer() {
        byte[] full = frame("CHAT_RESULT", "MODIFY", "text");
        byte[] truncated = new byte[full.length - 3];
        System.arraycopy(full, 0, truncated, 0, truncated.length);

        assertThatThrownBy(() -> SignedVelocityFrame.decode(truncated)).isInstanceOf(UncheckedIOException.class);
    }

    private static byte[] frame(String source, String result, @Nullable String modified) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(PLAYER.toString());
            out.writeUTF(source);
            out.writeUTF(result);
            if (modified != null) {
                out.writeUTF(modified);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
