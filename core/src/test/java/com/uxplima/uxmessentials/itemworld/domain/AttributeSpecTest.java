package com.uxplima.uxmessentials.itemworld.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The pure validation behind {@code /itemedit attribute add}: {@link AttributeSpec#of} normalises the attribute
 * id to a lowercase {@code namespace:path} (stripping a legacy {@code generic.} category prefix), lower-cases the
 * slot token and defaults it to {@code any}, keeps the amount as given, and rejects a blank id or a non-finite
 * amount.
 */
class AttributeSpecTest {

    @Test
    void aFlatIdGetsTheMinecraftNamespaceAndTheDefaultSlot() {
        Optional<AttributeSpec> spec = AttributeSpec.of("attack_damage", 5.0, null);
        assertThat(spec).isPresent();
        assertThat(spec.orElseThrow().attributeId()).isEqualTo("minecraft:attack_damage");
        assertThat(spec.orElseThrow().amount()).isEqualTo(5.0);
        assertThat(spec.orElseThrow().slotGroup()).isEqualTo("any");
    }

    @Test
    void aLegacyCategoryPrefixIsStripped() {
        assertThat(AttributeSpec.of("generic.attack_damage", 2.0, "HAND")
                        .orElseThrow()
                        .attributeId())
                .isEqualTo("minecraft:attack_damage");
    }

    @Test
    void anExplicitSlotIsLowercased() {
        assertThat(AttributeSpec.of("attack_damage", 1.0, "MainHand")
                        .orElseThrow()
                        .slotGroup())
                .isEqualTo("mainhand");
    }

    @Test
    void anExplicitNamespaceIsKept() {
        assertThat(AttributeSpec.of("minecraft:max_health", 4.0, "")
                        .orElseThrow()
                        .attributeId())
                .isEqualTo("minecraft:max_health");
    }

    @Test
    void aBlankIdOrNonFiniteAmountIsRejected() {
        assertThat(AttributeSpec.of("  ", 5.0, null)).isEmpty();
        assertThat(AttributeSpec.of("attack_damage", Double.NaN, null)).isEmpty();
        assertThat(AttributeSpec.of("attack_damage", Double.POSITIVE_INFINITY, null))
                .isEmpty();
    }
}
