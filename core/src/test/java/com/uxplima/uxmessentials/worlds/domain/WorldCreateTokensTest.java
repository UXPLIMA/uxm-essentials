package com.uxplima.uxmessentials.worlds.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The environment and type words an operator types after {@code /worlds create <name>}. */
class WorldCreateTokensTest {

    @Test
    void aKnownWordIsReadInAnyCase() {
        assertThat(WorldEnvironment.parse("the_end")).contains(WorldEnvironment.THE_END);
        assertThat(WorldGenType.parse(" Large_Biomes ")).contains(WorldGenType.LARGE_BIOMES);
    }

    @Test
    void aTypoIsNotReadAsAnyValue() {
        assertThat(WorldEnvironment.parse("nehter")).isEmpty();
        assertThat(WorldGenType.parse("flatt")).isEmpty();
    }
}
