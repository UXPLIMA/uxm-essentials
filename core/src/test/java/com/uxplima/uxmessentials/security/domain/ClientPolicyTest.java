package com.uxplima.uxmessentials.security.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The pure client-brand decision across the three modes: a block-list denies its entries and passes the rest, an
 * allow-list does the reverse, and flag mode never denies but marks a listed brand. Matching is case-insensitive
 * and a missing brand is treated as the empty brand.
 */
class ClientPolicyTest {

    @Test
    void blockListDeniesAListedBrandAndAllowsTheRest() {
        ClientPolicy policy = new ClientPolicy(ClientIdMode.BLOCK_LIST, Set.of("wurst", "impact"));

        assertThat(policy.judge("wurst")).isEqualTo(new ClientVerdict(false, true));
        assertThat(policy.judge("vanilla")).isEqualTo(new ClientVerdict(true, false));
    }

    @Test
    void allowListAllowsOnlyListedBrands() {
        ClientPolicy policy = new ClientPolicy(ClientIdMode.ALLOW_LIST, Set.of("vanilla", "fabric"));

        assertThat(policy.judge("fabric").allowed()).isTrue();
        assertThat(policy.judge("wurst").allowed()).isFalse();
        assertThat(policy.judge("wurst").flagged()).isTrue();
    }

    @Test
    void flagModeNeverDeniesButMarksAListedBrand() {
        ClientPolicy policy = new ClientPolicy(ClientIdMode.FLAG, Set.of("wurst"));

        assertThat(policy.judge("wurst")).isEqualTo(new ClientVerdict(true, true));
        assertThat(policy.judge("vanilla")).isEqualTo(new ClientVerdict(true, false));
    }

    @Test
    void matchingIsCaseInsensitiveAndTrimsWhitespace() {
        ClientPolicy policy = new ClientPolicy(ClientIdMode.BLOCK_LIST, Set.of("Wurst"));

        assertThat(policy.judge("  wURST ").allowed()).isFalse();
    }

    @Test
    void aMissingBrandIsTheEmptyBrand() {
        ClientPolicy block = new ClientPolicy(ClientIdMode.BLOCK_LIST, Set.of("wurst"));
        ClientPolicy allow = new ClientPolicy(ClientIdMode.ALLOW_LIST, Set.of("vanilla"));

        // Nothing reported: harmless under a block-list, but rejected when only known brands are allowed.
        assertThat(block.judge(null).allowed()).isTrue();
        assertThat(allow.judge(null).allowed()).isFalse();
    }
}
