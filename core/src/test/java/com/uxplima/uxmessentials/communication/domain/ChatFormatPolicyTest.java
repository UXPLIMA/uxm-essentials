package com.uxplima.uxmessentials.communication.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The pure selection contract of {@link ChatFormatPolicy}: {@link ChatFormatPolicy#formatFor(String)} returns the
 * group override when one matches (case-insensitively), and falls through to the default for an unknown group, a
 * null/blank group, or an empty override map. The input validation is exercised too, since the record is built from
 * operator config and must reject a blank default format or a null string field.
 */
class ChatFormatPolicyTest {

    private static final String DEFAULT = "<display_name>: <message>";

    @Test
    void formatForReturnsTheGroupOverrideWhenOneMatches() {
        ChatFormatPolicy policy = policy(Map.of("admin", "<red><display_name>: <message>"));

        assertThat(policy.formatFor("admin")).isEqualTo("<red><display_name>: <message>");
    }

    @Test
    void formatForMatchesTheGroupCaseInsensitively() {
        ChatFormatPolicy policy = policy(Map.of("admin", "<red><display_name>: <message>"));

        assertThat(policy.formatFor("ADMIN")).isEqualTo("<red><display_name>: <message>");
    }

    @Test
    void formatForFallsThroughToTheDefaultForAnUnknownGroup() {
        ChatFormatPolicy policy = policy(Map.of("admin", "<red><message>"));

        assertThat(policy.formatFor("member")).isEqualTo(DEFAULT);
    }

    @Test
    void formatForFallsThroughToTheDefaultForANullOrBlankGroup() {
        ChatFormatPolicy policy = policy(Map.of("admin", "<red><message>"));

        assertThat(policy.formatFor(null)).isEqualTo(DEFAULT);
        assertThat(policy.formatFor("  ")).isEqualTo(DEFAULT);
    }

    @Test
    void formatForReturnsTheDefaultWhenThereAreNoOverrides() {
        ChatFormatPolicy policy = policy(Map.of());

        assertThat(policy.formatFor("admin")).isEqualTo(DEFAULT);
    }

    @Test
    void aDisabledPolicyIsOffButStillHasARenderableDefault() {
        ChatFormatPolicy policy = ChatFormatPolicy.disabled();

        assertThat(policy.enabled()).isFalse();
        assertThat(policy.formatFor("admin")).isEqualTo(ChatFormatPolicy.vanillaFormat());
    }

    @Test
    void aBlankDefaultFormatIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ChatFormatPolicy(true, "   ", Map.of(), false, "", ""));
    }

    private static ChatFormatPolicy policy(Map<String, String> groupFormats) {
        return new ChatFormatPolicy(true, DEFAULT, groupFormats, false, "", "");
    }
}
