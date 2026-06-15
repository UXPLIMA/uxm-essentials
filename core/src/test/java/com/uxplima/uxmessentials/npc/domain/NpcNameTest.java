package com.uxplima.uxmessentials.npc.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NpcNameTest {

    @Test
    void normalisesToLowercaseAndTrims() {
        assertThat(NpcName.of("  Guide  ").value()).isEqualTo("guide");
    }

    @Test
    void isCaseInsensitiveForIdentity() {
        assertThat(NpcName.of("Guide")).isEqualTo(NpcName.of("guide"));
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> NpcName.of("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOverlongInput() {
        String tooLong = "a".repeat(NpcName.MAX_LENGTH + 1);
        assertThatThrownBy(() -> NpcName.of(tooLong)).isInstanceOf(IllegalArgumentException.class);
    }
}
