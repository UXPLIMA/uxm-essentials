package com.uxplima.uxmessentials.discordlink.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiscordIdTest {

    @Test
    void acceptsASnowflakeStringAndStrips() {
        assertThat(DiscordId.of("  123456789012345678  ").value()).isEqualTo("123456789012345678");
    }

    @Test
    void rejectsBlankNonNumericTooShortOrTooLong() {
        assertThatThrownBy(() -> DiscordId.of("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordId.of("not-a-number")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DiscordId.of("12345")).isInstanceOf(IllegalArgumentException.class); // too short
        assertThatThrownBy(() -> DiscordId.of("123456789012345678901"))
                .isInstanceOf(IllegalArgumentException.class); // too long
    }
}
