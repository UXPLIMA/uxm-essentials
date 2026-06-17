package com.uxplima.uxmessentials.holograms.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HologramCommandTest {

    @Test
    void resolvesAbsoluteCoordinates() {
        assertThat(HologramCommand.resolveCoord("12.5", 100.0)).isEqualTo(12.5);
        assertThat(HologramCommand.resolveCoord("-3", 100.0)).isEqualTo(-3.0);
    }

    @Test
    void resolvesTildeRelativeToTheBase() {
        assertThat(HologramCommand.resolveCoord("~", 64.0)).isEqualTo(64.0);
        assertThat(HologramCommand.resolveCoord("~2", 64.0)).isEqualTo(66.0);
        assertThat(HologramCommand.resolveCoord("~-1.5", 64.0)).isEqualTo(62.5);
    }

    @Test
    void rejectsUnparseableTokens() {
        assertThat(HologramCommand.resolveCoord("here", 0.0)).isNull();
        assertThat(HologramCommand.resolveCoord("~~", 0.0)).isNull();
    }
}
