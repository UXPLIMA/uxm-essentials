package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HologramTokensTest {

    @Test
    void detectsBuiltInTokensOnly() {
        assertThat(HologramTokens.hasToken("hi {player}")).isTrue();
        assertThat(HologramTokens.hasToken("page {page} of {pages}")).isTrue();
        assertThat(HologramTokens.hasToken("plain text")).isFalse();
        assertThat(HologramTokens.hasToken("a %papi% token")).isFalse();
    }

    @Test
    void substitutesPlayerPageAndPages() {
        assertThat(HologramTokens.resolve("{player} on page {page}/{pages}", "Steve", 2, 5))
                .isEqualTo("Steve on page 2/5");
    }

    @Test
    void leavesTextWithoutTokensUntouched() {
        assertThat(HologramTokens.resolve("plain line", "Steve", 1, 1)).isEqualTo("plain line");
    }
}
