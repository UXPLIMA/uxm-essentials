package com.uxplima.uxmessentials.npc.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Covers the per-key validation {@link NpcTypeData} exposes to the command: which keys are known, and which
 * values each key accepts (a boolean for {@code baby}/{@code charged}, a positive integer for {@code size}/{@code
 * villager_level}, and any non-blank name for the villager type/profession). The per-type apply path runs under
 * MockBukkit in {@code NpcRendererTest}; this is the pure value-shape contract.
 */
class NpcTypeDataTest {

    @Test
    void recognisesEveryCuratedKeyCaseInsensitively() {
        assertThat(NpcTypeData.isKnownKey("baby")).isTrue();
        assertThat(NpcTypeData.isKnownKey("SIZE")).isTrue();
        assertThat(NpcTypeData.isKnownKey("charged")).isTrue();
        assertThat(NpcTypeData.isKnownKey("villager_type")).isTrue();
        assertThat(NpcTypeData.isKnownKey("villager_profession")).isTrue();
        assertThat(NpcTypeData.isKnownKey("villager_level")).isTrue();
        assertThat(NpcTypeData.isKnownKey("nonsense")).isFalse();
    }

    @Test
    void validatesBooleanKeys() {
        assertThat(NpcTypeData.isValidValue("baby", "true")).isTrue();
        assertThat(NpcTypeData.isValidValue("charged", "FALSE")).isTrue();
        assertThat(NpcTypeData.isValidValue("baby", "yes")).isFalse();
        assertThat(NpcTypeData.isValidValue("charged", "1")).isFalse();
    }

    @Test
    void validatesIntegerKeys() {
        assertThat(NpcTypeData.isValidValue("size", "4")).isTrue();
        assertThat(NpcTypeData.isValidValue("villager_level", "3")).isTrue();
        assertThat(NpcTypeData.isValidValue("size", "0")).isFalse();
        assertThat(NpcTypeData.isValidValue("size", "-1")).isFalse();
        assertThat(NpcTypeData.isValidValue("villager_level", "abc")).isFalse();
    }

    @Test
    void validatesNameKeys() {
        assertThat(NpcTypeData.isValidValue("villager_type", "desert")).isTrue();
        assertThat(NpcTypeData.isValidValue("villager_profession", "librarian")).isTrue();
        assertThat(NpcTypeData.isValidValue("villager_type", " ")).isFalse();
    }
}
