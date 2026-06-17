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

    @Test
    void recognisesTheNewVariantKeys() {
        assertThat(NpcTypeData.isKnownKey("horse_color")).isTrue();
        assertThat(NpcTypeData.isKnownKey("HORSE_MARKINGS")).isTrue();
        assertThat(NpcTypeData.isKnownKey("horse_style")).isTrue();
        assertThat(NpcTypeData.isKnownKey("llama_variant")).isTrue();
        assertThat(NpcTypeData.isKnownKey("sheep_color")).isTrue();
        assertThat(NpcTypeData.isKnownKey("wolf_collar")).isTrue();
        assertThat(NpcTypeData.isKnownKey("shulker_color")).isTrue();
        assertThat(NpcTypeData.isKnownKey("shulker_peek")).isTrue();
        assertThat(NpcTypeData.isKnownKey("panda_gene")).isTrue();
        assertThat(NpcTypeData.isKnownKey("goat_screaming")).isTrue();
        assertThat(NpcTypeData.isKnownKey("allay_dancing")).isTrue();
        assertThat(NpcTypeData.isKnownKey("piglin_dancing")).isTrue();
        assertThat(NpcTypeData.isKnownKey("camel_dash")).isTrue();
        assertThat(NpcTypeData.isKnownKey("bee_nectar")).isTrue();
        assertThat(NpcTypeData.isKnownKey("vex_charging")).isTrue();
        assertThat(NpcTypeData.isKnownKey("parrot_variant")).isTrue();
        assertThat(NpcTypeData.isKnownKey("axolotl_variant")).isTrue();
        assertThat(NpcTypeData.isKnownKey("fox_type")).isTrue();
        assertThat(NpcTypeData.isKnownKey("rabbit_type")).isTrue();
        assertThat(NpcTypeData.isKnownKey("cat_variant")).isTrue();
        assertThat(NpcTypeData.isKnownKey("FROG_VARIANT")).isTrue();
    }

    @Test
    void validatesCatAndFrogVariantNamesAgainstTheKnownSet() {
        // The cat/frog variants are dynamic-registry values; the command validates the name against the vanilla
        // set before the lib resolves the holder off the live server (case-insensitive).
        assertThat(NpcTypeData.isValidValue("cat_variant", "calico")).isTrue();
        assertThat(NpcTypeData.isValidValue("cat_variant", "ALL_BLACK")).isTrue();
        assertThat(NpcTypeData.isValidValue("cat_variant", "jellie")).isTrue();
        assertThat(NpcTypeData.isValidValue("cat_variant", "not_a_cat")).isFalse();
        assertThat(NpcTypeData.isValidValue("frog_variant", "temperate")).isTrue();
        assertThat(NpcTypeData.isValidValue("frog_variant", "Warm")).isTrue();
        assertThat(NpcTypeData.isValidValue("frog_variant", "cold")).isTrue();
        assertThat(NpcTypeData.isValidValue("frog_variant", "tropical")).isFalse();
    }

    @Test
    void validatesBoundedIntegerVariantKeys() {
        // Each variant key accepts a non-negative int within its own range; zero is valid (the first variant).
        assertThat(NpcTypeData.isValidValue("horse_color", "6")).isTrue();
        assertThat(NpcTypeData.isValidValue("horse_color", "0")).isTrue();
        assertThat(NpcTypeData.isValidValue("horse_color", "7")).isFalse();
        assertThat(NpcTypeData.isValidValue("horse_markings", "4")).isTrue();
        assertThat(NpcTypeData.isValidValue("horse_markings", "5")).isFalse();
        assertThat(NpcTypeData.isValidValue("llama_variant", "3")).isTrue();
        assertThat(NpcTypeData.isValidValue("llama_variant", "4")).isFalse();
        assertThat(NpcTypeData.isValidValue("parrot_variant", "4")).isTrue();
        assertThat(NpcTypeData.isValidValue("axolotl_variant", "4")).isTrue();
        assertThat(NpcTypeData.isValidValue("fox_type", "1")).isTrue();
        assertThat(NpcTypeData.isValidValue("fox_type", "2")).isFalse();
        assertThat(NpcTypeData.isValidValue("fox_type", "-1")).isFalse();
        assertThat(NpcTypeData.isValidValue("shulker_peek", "0")).isTrue();
        assertThat(NpcTypeData.isValidValue("shulker_peek", "100")).isTrue();
        assertThat(NpcTypeData.isValidValue("shulker_peek", "101")).isFalse();
        assertThat(NpcTypeData.isValidValue("panda_gene", "6")).isTrue();
        assertThat(NpcTypeData.isValidValue("panda_gene", "7")).isFalse();
    }

    @Test
    void validatesHorseStyleNames() {
        // horse_style is the named alias of the markings: a Horse.Style name is valid (case-insensitive); an int or
        // an unknown name is not (a raw markings id goes through horse_markings instead).
        assertThat(NpcTypeData.isValidValue("horse_style", "white_dots")).isTrue();
        assertThat(NpcTypeData.isValidValue("horse_style", "NONE")).isTrue();
        assertThat(NpcTypeData.isValidValue("horse_style", "black_dots")).isTrue();
        assertThat(NpcTypeData.isValidValue("horse_style", "stripes")).isFalse();
        assertThat(NpcTypeData.isValidValue("horse_style", "3")).isFalse();
    }

    @Test
    void validatesRabbitTypeIncludingTheKillerVariant() {
        // The six coats are 0-5; 99 is the killer (toast) rabbit, a valid wire value even though it is out of the
        // 0-5 run.
        assertThat(NpcTypeData.isValidValue("rabbit_type", "0")).isTrue();
        assertThat(NpcTypeData.isValidValue("rabbit_type", "5")).isTrue();
        assertThat(NpcTypeData.isValidValue("rabbit_type", "99")).isTrue();
        assertThat(NpcTypeData.isValidValue("rabbit_type", "6")).isFalse();
        assertThat(NpcTypeData.isValidValue("rabbit_type", "98")).isFalse();
    }

    @Test
    void sheepColorAcceptsADyeColorNameOrARawId() {
        assertThat(NpcTypeData.isValidValue("sheep_color", "red")).isTrue();
        assertThat(NpcTypeData.isValidValue("sheep_color", "LIGHT_BLUE")).isTrue();
        assertThat(NpcTypeData.isValidValue("sheep_color", "0")).isTrue();
        assertThat(NpcTypeData.isValidValue("sheep_color", "15")).isTrue();
        assertThat(NpcTypeData.isValidValue("sheep_color", "16")).isFalse();
        assertThat(NpcTypeData.isValidValue("sheep_color", "not_a_color")).isFalse();
    }

    @Test
    void wolfCollarAcceptsADyeColorNameOrARawId() {
        assertThat(NpcTypeData.isValidValue("wolf_collar", "red")).isTrue();
        assertThat(NpcTypeData.isValidValue("wolf_collar", "LIGHT_BLUE")).isTrue();
        assertThat(NpcTypeData.isValidValue("wolf_collar", "0")).isTrue();
        assertThat(NpcTypeData.isValidValue("wolf_collar", "15")).isTrue();
        assertThat(NpcTypeData.isValidValue("wolf_collar", "16")).isFalse();
        assertThat(NpcTypeData.isValidValue("wolf_collar", "not_a_color")).isFalse();
    }

    @Test
    void shulkerColorAcceptsADyeColorNameOrARawId() {
        assertThat(NpcTypeData.isValidValue("shulker_color", "red")).isTrue();
        assertThat(NpcTypeData.isValidValue("shulker_color", "LIGHT_BLUE")).isTrue();
        assertThat(NpcTypeData.isValidValue("shulker_color", "0")).isTrue();
        assertThat(NpcTypeData.isValidValue("shulker_color", "15")).isTrue();
        assertThat(NpcTypeData.isValidValue("shulker_color", "16")).isFalse();
        assertThat(NpcTypeData.isValidValue("shulker_color", "not_a_color")).isFalse();
    }

    @Test
    void booleanStateVariantsAcceptOnlyTrueOrFalse() {
        assertThat(NpcTypeData.isValidValue("goat_screaming", "true")).isTrue();
        assertThat(NpcTypeData.isValidValue("allay_dancing", "FALSE")).isTrue();
        assertThat(NpcTypeData.isValidValue("piglin_dancing", "true")).isTrue();
        assertThat(NpcTypeData.isValidValue("camel_dash", "false")).isTrue();
        assertThat(NpcTypeData.isValidValue("bee_nectar", "true")).isTrue();
        assertThat(NpcTypeData.isValidValue("vex_charging", "false")).isTrue();
        assertThat(NpcTypeData.isValidValue("goat_screaming", "yes")).isFalse();
        assertThat(NpcTypeData.isValidValue("camel_dash", "1")).isFalse();
        assertThat(NpcTypeData.isValidValue("vex_charging", "maybe")).isFalse();
    }
}
