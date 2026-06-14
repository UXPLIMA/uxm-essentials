package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import org.junit.jupiter.api.Test;

/**
 * Covers the adapter's {@link AnimationRegistry}: a {@code %anim_<name>%} token expands to the named animation's frame
 * at the captured tick, FRAMES cycle by tick arithmetic, the stateful SCROLL/GRADIENT animators advance across ticks,
 * an unknown token is left verbatim, and several tokens in one source all expand. The animators are pure text, so the
 * registry is asserted without a live board.
 */
class AnimationRegistryTest {

    @Test
    void aFramesTokenResolvesToTheFrameForTheTick() {
        AnimationRegistry registry = new AnimationRegistry(List.of(frames("blink", List.of("ON", "OFF"), 1)));

        // FRAMES is pure tick arithmetic: tick 0 → index 0, tick 1 → index 1, tick 2 → wraps to index 0.
        assertThat(registry.resolve("[%anim_blink%]", 0L)).isEqualTo("[ON]");
        assertThat(registry.resolve("[%anim_blink%]", 1L)).isEqualTo("[OFF]");
        assertThat(registry.resolve("[%anim_blink%]", 2L)).isEqualTo("[ON]");
    }

    @Test
    void aFramesTokenHonoursTheInterval() {
        AnimationRegistry registry = new AnimationRegistry(List.of(frames("blink", List.of("ON", "OFF"), 3)));

        // With interval 3, the frame only flips every third tick.
        assertThat(registry.resolve("%anim_blink%", 0L)).isEqualTo("ON");
        assertThat(registry.resolve("%anim_blink%", 2L)).isEqualTo("ON");
        assertThat(registry.resolve("%anim_blink%", 3L)).isEqualTo("OFF");
    }

    @Test
    void aScrollAnimationAdvancesAcrossTicks() {
        AnimationDef def = AnimationDef.scroll(
                new AnimationSpec("roll", AnimationSpec.AnimationType.SCROLL, List.of("ABCDE"), 1),
                new AnimationDef.Scroll(3, ""));
        AnimationRegistry registry = new AnimationRegistry(List.of(def));

        String atStart = registry.resolve("%anim_roll%", registry.tick());
        registry.advance();
        String afterOne = registry.resolve("%anim_roll%", registry.tick());
        registry.advance();
        String afterTwo = registry.resolve("%anim_roll%", registry.tick());

        // The marquee window slides one character per advance, so consecutive ticks differ.
        assertThat(atStart).isNotEqualTo(afterOne);
        assertThat(afterOne).isNotEqualTo(afterTwo);
    }

    @Test
    void aGradientAnimationAdvancesAcrossTicks() {
        AnimationDef def = AnimationDef.gradient(
                new AnimationSpec("glow", AnimationSpec.AnimationType.GRADIENT, List.of("HELLO"), 1),
                new AnimationDef.Gradient(List.of("#ff0000", "#0000ff"), 8));
        AnimationRegistry registry = new AnimationRegistry(List.of(def));

        String atStart = registry.resolve("%anim_glow%", registry.tick());
        registry.advance();
        String afterOne = registry.resolve("%anim_glow%", registry.tick());

        // The per-character colour phase shifts each advance, so the serialized MiniMessage differs tick to tick.
        assertThat(atStart).isNotEqualTo(afterOne);
    }

    @Test
    void anUnknownTokenIsLeftVerbatim() {
        AnimationRegistry registry = new AnimationRegistry(List.of(frames("blink", List.of("ON"), 1)));

        // A typo'd animation name stays on the board so the operator sees it rather than a silent blank.
        assertThat(registry.resolve("<gray>%anim_typo%", 0L)).isEqualTo("<gray>%anim_typo%");
    }

    @Test
    void multipleTokensInOneSourceAllExpand() {
        AnimationRegistry registry =
                new AnimationRegistry(List.of(frames("a", List.of("X"), 1), frames("b", List.of("Y"), 1)));

        assertThat(registry.resolve("%anim_a% and %anim_b% and %anim_a%", 0L)).isEqualTo("X and Y and X");
    }

    @Test
    void aSourceWithNoTokenIsReturnedUnchanged() {
        AnimationRegistry registry = new AnimationRegistry(List.of(frames("a", List.of("X"), 1)));

        assertThat(registry.resolve("<gray>plain line", 0L)).isEqualTo("<gray>plain line");
    }

    @Test
    void advanceStepsTheGlobalTick() {
        AnimationRegistry registry = new AnimationRegistry(List.of());

        long before = registry.tick();
        registry.advance();
        assertThat(registry.tick()).isEqualTo(before + 1);
    }

    private static AnimationDef frames(String name, List<String> frames, int interval) {
        return AnimationDef.frames(new AnimationSpec(name, AnimationSpec.AnimationType.FRAMES, frames, interval));
    }
}
