package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class HologramTypeTest {

    @Test
    void parsesEveryNameCaseInsensitively() {
        assertThat(HologramType.parse("text")).contains(HologramType.TEXT);
        assertThat(HologramType.parse("Item")).contains(HologramType.ITEM);
        assertThat(HologramType.parse("BLOCK")).contains(HologramType.BLOCK);
    }

    @Test
    void anUnknownNullOrBlankTokenParsesToEmpty() {
        assertThat(HologramType.parse("hologram")).isEmpty();
        assertThat(HologramType.parse("")).isEmpty();
        assertThat(HologramType.parse(null)).isEqualTo(Optional.empty());
    }

    @Test
    void onlyTextRequiresLines() {
        assertThat(HologramType.TEXT.requiresLines()).isTrue();
        assertThat(HologramType.ITEM.requiresLines()).isFalse();
        assertThat(HologramType.BLOCK.requiresLines()).isFalse();
    }
}
