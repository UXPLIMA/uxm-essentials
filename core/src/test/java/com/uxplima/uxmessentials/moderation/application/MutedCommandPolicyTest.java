package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The pure mute-command-block rule: a label on the configured list is blocked, the match ignores case and a
 * leading slash on either the configured entry or the queried label, and an empty list blocks nothing (so the
 * adapter can short-circuit before any mute lookup).
 */
class MutedCommandPolicyTest {

    @Test
    void blocksAConfiguredCommandLabel() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("me", "msg", "mail"));

        assertThat(policy.isBlocked("me")).isTrue();
        assertThat(policy.isBlocked("mail")).isTrue();
        assertThat(policy.isBlocked("home")).isFalse();
    }

    @Test
    void matchIgnoresCaseAndLeadingSlashOnBothSides() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("/Me", " msg "));

        assertThat(policy.isBlocked("ME")).isTrue();
        assertThat(policy.isBlocked("/me")).isTrue();
        assertThat(policy.isBlocked("MSG")).isTrue();
    }

    @Test
    void anEmptyListBlocksNothingAndReportsEmpty() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of());

        assertThat(policy.isEmpty()).isTrue();
        assertThat(policy.isBlocked("me")).isFalse();
    }

    @Test
    void blankAndSlashOnlyEntriesAreDroppedSoTheyNeverMatchEverything() {
        MutedCommandPolicy policy = new MutedCommandPolicy(Set.of("", "  ", "/", "me"));

        assertThat(policy.isEmpty()).isFalse();
        assertThat(policy.isBlocked("me")).isTrue();
        assertThat(policy.isBlocked("anything")).isFalse();
        assertThat(policy.isBlocked("")).isFalse();
    }
}
