package com.uxplima.uxmessentials.shared.display;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;

import org.junit.jupiter.api.Test;

class AnimationSpecTest {

    @Test
    void framesCycleByTickRespectingInterval() {
        AnimationSpec spec = new AnimationSpec("pulse", AnimationSpec.AnimationType.FRAMES, List.of("a", "b", "c"), 5);

        // Tick 0..4 → frame index 0; 5..9 → index 1; 10..14 → index 2; 15 wraps to index 0.
        assertThat(spec.frameAt(0)).isEqualTo("a");
        assertThat(spec.frameAt(4)).isEqualTo("a");
        assertThat(spec.frameAt(5)).isEqualTo("b");
        assertThat(spec.frameAt(9)).isEqualTo("b");
        assertThat(spec.frameAt(10)).isEqualTo("c");
        assertThat(spec.frameAt(14)).isEqualTo("c");
        assertThat(spec.frameAt(15)).isEqualTo("a");
    }

    @Test
    void framesWithIntervalOneStepEachTick() {
        AnimationSpec spec = new AnimationSpec("fast", AnimationSpec.AnimationType.FRAMES, List.of("x", "y"), 1);
        assertThat(spec.frameAt(0)).isEqualTo("x");
        assertThat(spec.frameAt(1)).isEqualTo("y");
        assertThat(spec.frameAt(2)).isEqualTo("x");
    }

    @Test
    void scrollAndGradientReturnTheFirstFrameUntilTheAdapterBindsRendering() {
        AnimationSpec scroll =
                new AnimationSpec("marquee", AnimationSpec.AnimationType.SCROLL, List.of("hello world"), 2);
        AnimationSpec gradient =
                new AnimationSpec("rainbow", AnimationSpec.AnimationType.GRADIENT, List.of("welcome"), 3);
        assertThat(scroll.frameAt(0)).isEqualTo("hello world");
        assertThat(scroll.frameAt(100)).isEqualTo("hello world");
        assertThat(gradient.frameAt(0)).isEqualTo("welcome");
        assertThat(gradient.frameAt(999)).isEqualTo("welcome");
    }

    @Test
    void blankNameRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AnimationSpec("  ", AnimationSpec.AnimationType.FRAMES, List.of("a"), 1));
    }

    @Test
    void emptyFramesRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AnimationSpec("n", AnimationSpec.AnimationType.FRAMES, List.of(), 1));
    }

    @Test
    void intervalBelowOneRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AnimationSpec("n", AnimationSpec.AnimationType.FRAMES, List.of("a"), 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AnimationSpec("n", AnimationSpec.AnimationType.FRAMES, List.of("a"), -1));
    }

    @Test
    void negativeTickRejected() {
        AnimationSpec spec = new AnimationSpec("pulse", AnimationSpec.AnimationType.FRAMES, List.of("a"), 1);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> spec.frameAt(-1));
    }

    @Test
    void framesListIsDefensivelyCopied() {
        var mutable = new java.util.ArrayList<>(List.of("a", "b"));
        AnimationSpec spec = new AnimationSpec("n", AnimationSpec.AnimationType.FRAMES, mutable, 1);
        mutable.clear();
        assertThat(spec.frames()).containsExactly("a", "b");
    }
}
