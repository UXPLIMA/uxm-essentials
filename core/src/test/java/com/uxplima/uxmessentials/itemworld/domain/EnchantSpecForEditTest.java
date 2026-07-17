package com.uxplima.uxmessentials.itemworld.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The over-vanilla-max branch of {@link EnchantSpec#forEdit} that backs {@code /itemedit enchant}. A request
 * within the enchant's vanilla max is untouched; a request above it clamps to the vanilla max when the operator
 * has not opted into over-max levels, and clamps only to the absolute hard ceiling when they have. A blank id is
 * rejected and a level below one floors to one, matching {@link EnchantSpec#of}.
 */
class EnchantSpecForEditTest {

    @Test
    void aRequestWithinTheVanillaMaxIsUntouched() {
        Optional<EnchantSpec> spec = EnchantSpec.forEdit("sharpness", 3, 5, false, 32_767);
        assertThat(spec).isPresent();
        assertThat(spec.orElseThrow().level()).isEqualTo(3);
        assertThat(spec.orElseThrow().clamped()).isFalse();
        assertThat(spec.orElseThrow().enchantId()).isEqualTo("minecraft:sharpness");
    }

    @Test
    void overVanillaMaxIsClampedToVanillaWhenNotAllowed() {
        Optional<EnchantSpec> spec = EnchantSpec.forEdit("sharpness", 10, 5, false, 32_767);
        assertThat(spec.orElseThrow().level()).isEqualTo(5);
        assertThat(spec.orElseThrow().clamped()).isTrue();
    }

    @Test
    void overVanillaMaxIsAllowedWhenFlagged() {
        Optional<EnchantSpec> spec = EnchantSpec.forEdit("sharpness", 10, 5, true, 32_767);
        assertThat(spec.orElseThrow().level()).isEqualTo(10);
        assertThat(spec.orElseThrow().clamped()).isFalse();
    }

    @Test
    void theHardCeilingStillBoundsAnOverMaxRequest() {
        Optional<EnchantSpec> spec = EnchantSpec.forEdit("sharpness", 99_999, 5, true, 1_000);
        assertThat(spec.orElseThrow().level()).isEqualTo(1_000);
        assertThat(spec.orElseThrow().clamped()).isTrue();
    }

    @Test
    void aBlankIdIsRejectedAndALowLevelFloorsToOne() {
        assertThat(EnchantSpec.forEdit("  ", 5, 5, false, 32_767)).isEmpty();
        assertThat(EnchantSpec.forEdit("minecraft:sharpness", 0, 5, false, 32_767)
                        .orElseThrow()
                        .level())
                .isEqualTo(1);
    }
}
