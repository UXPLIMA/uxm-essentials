package com.uxplima.uxmessentials.scoreboard.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.display.AnimationSpec;
import org.jspecify.annotations.NullMarked;

/**
 * One parsed {@code animations { <name> { … } }} entry as the codec hands it to the {@link AnimationRegistry}: the pure
 * {@link AnimationSpec} (name, type, frames, interval) plus the extra parameters the uxmLib animators need that the
 * pure spec does not model. {@code AnimationSpec} is a {@code :core} value object with no rendering library, so the
 * {@link AnimationSpec.AnimationType#SCROLL SCROLL} window/separator and the
 * {@link AnimationSpec.AnimationType#GRADIENT GRADIENT} colour-stops/steps live here, in the adapter, where the
 * registry binds uxmLib's {@code ScrollingText}/{@code GradientText}.
 *
 * <p>The extra fields are only consulted for the matching type — {@link #scroll} for SCROLL, {@link #gradient} for
 * GRADIENT — and are empty for FRAMES, which the spec resolves on its own. Both are {@link Optional} so a missing block
 * falls back to the animator's own defaults rather than failing the parse.
 *
 * @param spec the pure animation spec; its {@code intervalTicks} drives the step cadence for every type
 * @param scroll the SCROLL window/separator, present only for a SCROLL spec
 * @param gradient the GRADIENT colour stops and sweep length, present only for a GRADIENT spec
 */
@NullMarked
public record AnimationDef(AnimationSpec spec, Optional<Scroll> scroll, Optional<Gradient> gradient) {

    public AnimationDef {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(scroll, "scroll");
        Objects.requireNonNull(gradient, "gradient");
    }

    /** A FRAMES animation, which needs no extra adapter parameters. */
    public static AnimationDef frames(AnimationSpec spec) {
        return new AnimationDef(spec, Optional.empty(), Optional.empty());
    }

    /** A SCROLL animation with the marquee window width and the wrap separator. */
    public static AnimationDef scroll(AnimationSpec spec, Scroll params) {
        return new AnimationDef(spec, Optional.of(params), Optional.empty());
    }

    /** A GRADIENT animation with its colour stops and sweep length. */
    public static AnimationDef gradient(AnimationSpec spec, Gradient params) {
        return new AnimationDef(spec, Optional.empty(), Optional.of(params));
    }

    /** The SCROLL marquee parameters: the visible window width in characters and the wrap-gap separator. */
    public record Scroll(int window, String separator) {
        public Scroll {
            Objects.requireNonNull(separator, "separator");
            if (window < 1) {
                throw new IllegalArgumentException("scroll window must be >= 1: " + window);
            }
        }
    }

    /** The GRADIENT parameters: the hex/named colour stops (at least two) and how many steps a full sweep takes. */
    public record Gradient(List<String> colors, int steps) {
        public Gradient {
            Objects.requireNonNull(colors, "colors");
            colors = List.copyOf(colors);
            if (colors.size() < 2) {
                throw new IllegalArgumentException("a gradient needs at least two colour stops");
            }
            if (steps < 1) {
                throw new IllegalArgumentException("gradient steps must be >= 1: " + steps);
            }
        }
    }
}
