package com.uxplima.uxmessentials.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The pure legacy-vs-MiniMessage decision of {@link LegacyChatCodes}: an affix carrying a {@code &c} or section-sign
 * colour/format code is legacy, a MiniMessage or plain affix is not, and a lone ampersand that introduces no code is
 * not enough to force the legacy parser. The adapter reads this to choose which serializer renders a prefix/suffix.
 */
class LegacyChatCodesTest {

    private static final char SECTION = '\u00A7';

    @Test
    void anAmpersandColourCodeIsLegacy() {
        assertThat(LegacyChatCodes.containsLegacyCodes("&c[VIP]")).isTrue();
    }

    @Test
    void aSectionSignColourCodeIsLegacy() {
        assertThat(LegacyChatCodes.containsLegacyCodes(SECTION + "c[VIP]")).isTrue();
    }

    @Test
    void aFormatAndHexMarkerCodeIsLegacy() {
        assertThat(LegacyChatCodes.containsLegacyCodes("&l&x&f&f&0&0&0&0bold")).isTrue();
        assertThat(LegacyChatCodes.containsLegacyCodes("&r reset")).isTrue();
    }

    @Test
    void theCodeCharacterIsMatchedCaseInsensitively() {
        assertThat(LegacyChatCodes.containsLegacyCodes("&C[VIP]")).isTrue();
        assertThat(LegacyChatCodes.containsLegacyCodes("&L[VIP]")).isTrue();
    }

    @Test
    void aMiniMessageAffixIsNotLegacy() {
        assertThat(LegacyChatCodes.containsLegacyCodes("<red>[VIP]")).isFalse();
    }

    @Test
    void aPlainAffixIsNotLegacy() {
        assertThat(LegacyChatCodes.containsLegacyCodes("[VIP]")).isFalse();
        assertThat(LegacyChatCodes.containsLegacyCodes("")).isFalse();
    }

    @Test
    void aLoneAmpersandWithNoCodeIsNotLegacy() {
        assertThat(LegacyChatCodes.containsLegacyCodes("Tom & Jerry")).isFalse();
        assertThat(LegacyChatCodes.containsLegacyCodes("&z not a code")).isFalse();
        assertThat(LegacyChatCodes.containsLegacyCodes("&")).isFalse();
    }
}
