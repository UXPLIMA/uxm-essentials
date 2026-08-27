package com.uxplima.uxmessentials.customcommands.domain;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A command's ordered chain of action steps, with the {@code delay:} tokens already folded into per-step offsets.
 *
 * <p>A {@code delay:} shifts everything after it rather than pausing a thread, so the timing of a chain can be read
 * straight down the file and nothing ever blocks waiting for it. Two limits keep a chain from outliving the reason
 * it was written: a single delay is clamped to a ceiling, and only so many delayed steps may be scheduled by one
 * execution. Both are operator settings, and both report what they trimmed rather than trimming quietly.
 */
public record ActionChain(List<ActionStep> steps, List<String> warnings) {

    public ActionChain {
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    /** The empty chain, which is what an absent {@code actions} or {@code requirement-deny} block reads as. */
    public static ActionChain empty() {
        return new ActionChain(List.of(), List.of());
    }

    /** Fold {@code tokens} into steps, applying {@code limits} and recording what they trimmed. */
    public static ActionChain of(List<String> tokens, ChainLimits limits) {
        Objects.requireNonNull(tokens, "tokens");
        Objects.requireNonNull(limits, "limits");
        List<ActionStep> steps = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Duration offset = Duration.ZERO;
        int delayed = 0;
        for (@Nullable String raw : tokens) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String token = raw.strip();
            Optional<Duration> declared = delayOf(token);
            if (declared.isPresent()) {
                offset = offset.plus(clamp(declared.get(), limits, token, warnings));
                continue;
            }
            if (isDelayToken(token)) {
                warnings.add("dropping unreadable delay '" + token + "'");
                continue;
            }
            if (!offset.isZero() && delayed >= limits.maxDelayedSteps()) {
                warnings.add("dropping '" + token + "': more than " + limits.maxDelayedSteps()
                        + " delayed steps in one chain");
                continue;
            }
            if (!offset.isZero()) {
                delayed++;
            }
            steps.add(new ActionStep(token, offset));
        }
        return new ActionChain(steps, warnings);
    }

    private static Duration clamp(Duration declared, ChainLimits limits, String token, List<String> warnings) {
        if (declared.compareTo(limits.maxDelay()) <= 0) {
            return declared;
        }
        warnings.add("clamping delay '" + token + "' to " + CommandDuration.format(limits.maxDelay()));
        return limits.maxDelay();
    }

    private static boolean isDelayToken(String token) {
        return token.toLowerCase(Locale.ROOT).startsWith("delay:");
    }

    private static Optional<Duration> delayOf(String token) {
        if (!isDelayToken(token)) {
            return Optional.empty();
        }
        return CommandDuration.parse(token.substring("delay:".length()));
    }

    /** Whether this chain has nothing to run, so a caller can skip the dispatch entirely. */
    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /**
     * What one execution may schedule: the longest a single {@code delay:} may ask for, and how many delayed steps
     * may ride behind it. Both come from the module's config so an operator can tighten them on a busy server.
     */
    public record ChainLimits(Duration maxDelay, int maxDelayedSteps) {

        public ChainLimits {
            Objects.requireNonNull(maxDelay, "maxDelay");
            if (maxDelay.isNegative()) {
                throw new IllegalArgumentException("maxDelay must not be negative: " + maxDelay);
            }
            if (maxDelayedSteps < 0) {
                throw new IllegalArgumentException("maxDelayedSteps must not be negative: " + maxDelayedSteps);
            }
        }

        /** The shipped defaults: a minute of delay and twenty delayed steps. */
        public static ChainLimits defaults() {
            return new ChainLimits(Duration.ofSeconds(60), 20);
        }
    }
}
