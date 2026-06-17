package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HologramAnimationsTest {

    @Test
    void aLineWithoutADirectiveIsReturnedUnchanged() {
        assertThat(HologramAnimations.expand("<green>Welcome", 7)).isEqualTo("<green>Welcome");
        assertThat(HologramAnimations.expand("plain text", 0)).isEqualTo("plain text");
    }

    @Test
    void rainbowWrapsTheTextWithThePhase() {
        assertThat(HologramAnimations.expand("<anim:rainbow>Spawn", 0)).isEqualTo("<rainbow:0>Spawn");
        assertThat(HologramAnimations.expand("<anim:rainbow>Spawn", 5)).isEqualTo("<rainbow:5>Spawn");
    }

    @Test
    void typewriterRevealsOneMoreCharacterPerFrameThenHoldsAndRestarts() {
        assertThat(HologramAnimations.expand("<anim:typewriter>abc", 0)).isEmpty();
        assertThat(HologramAnimations.expand("<anim:typewriter>abc", 1)).isEqualTo("a");
        assertThat(HologramAnimations.expand("<anim:typewriter>abc", 3)).isEqualTo("abc");
        // Past the text length it holds the full text (period = length + hold), then the cycle restarts at 0.
        assertThat(HologramAnimations.expand("<anim:typewriter>abc", 4)).isEqualTo("abc");
        assertThat(HologramAnimations.expand("<anim:typewriter>abc", 9)).isEmpty();
    }

    @Test
    void scrollRotatesTheTextWithATrailingGap() {
        // "ab" + "   " gap = "ab   " (length 5); phase 1 rotates left by one.
        assertThat(HologramAnimations.expand("<anim:scroll>ab", 0)).isEqualTo("ab   ");
        assertThat(HologramAnimations.expand("<anim:scroll>ab", 1)).isEqualTo("b   a");
        // A full period returns to the start.
        assertThat(HologramAnimations.expand("<anim:scroll>ab", 5)).isEqualTo("ab   ");
    }

    @Test
    void anUnknownTypeIsLeftUnchanged() {
        assertThat(HologramAnimations.expand("<anim:spin>x", 3)).isEqualTo("<anim:spin>x");
    }

    @Test
    void stripDirectiveRemovesTheLeadingDirectiveOnly() {
        assertThat(HologramAnimations.stripDirective("<anim:rainbow>Hi")).isEqualTo("Hi");
        assertThat(HologramAnimations.stripDirective("<anim:typewriter>%player%"))
                .isEqualTo("%player%");
        assertThat(HologramAnimations.stripDirective("<green>Hi")).isEqualTo("<green>Hi");
    }
}
