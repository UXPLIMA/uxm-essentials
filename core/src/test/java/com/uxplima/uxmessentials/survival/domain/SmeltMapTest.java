package com.uxplima.uxmessentials.survival.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Pins the pure autosmelt lookup: a mapped drop smelts, an unmapped one passes through, and lookup is case-folded. */
class SmeltMapTest {

    private final SmeltMap map = new SmeltMap(Map.of("RAW_IRON", "IRON_INGOT", "COBBLESTONE", "STONE"));

    @Test
    void aMappedDropSmeltsToItsResult() {
        assertThat(map.smelted("RAW_IRON")).contains("IRON_INGOT");
        assertThat(map.smelted("COBBLESTONE")).contains("STONE");
    }

    @Test
    void lookupIsCaseInsensitiveOnBothSides() {
        assertThat(new SmeltMap(Map.of("raw_gold", "gold_ingot")).smelted("RAW_GOLD"))
                .contains("GOLD_INGOT");
    }

    @Test
    void anUnmappedDropIsLeftUntouched() {
        assertThat(map.smelted("DIAMOND")).isEmpty();
    }

    @Test
    void anEmptyMapReportsItself() {
        assertThat(new SmeltMap(Map.of()).isEmpty()).isTrue();
        assertThat(map.isEmpty()).isFalse();
    }
}
