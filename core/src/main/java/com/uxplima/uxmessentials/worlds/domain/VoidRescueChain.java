package com.uxplima.uxmessentials.worlds.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A world's void-rescue policy: the ordered steps walked when a player falls out of that world, until one
 * resolves to a position. An empty chain means the world is not managed and the fall stays a vanilla death.
 *
 * <p>Parsing is all-or-nothing on purpose. A chain with one unparseable token is refused outright rather
 * than silently losing that step, so {@code /worlds set} rejects the typo at the moment it is typed instead
 * of leaving an operator with a rescue that quietly never fires.
 */
public record VoidRescueChain(List<VoidRescueStep> steps) {

    private static final String SEPARATOR = ";";

    public VoidRescueChain {
        Objects.requireNonNull(steps, "steps");
        steps = List.copyOf(steps);
    }

    /** The chain of a world that is not managed. */
    public static VoidRescueChain none() {
        return new VoidRescueChain(List.of());
    }

    /** Parse a {@code ;}-separated setting value; blank is the valid empty chain, a bad token is a refusal. */
    public static Optional<VoidRescueChain> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.isBlank()) {
            return Optional.of(none());
        }
        List<VoidRescueStep> parsed = new ArrayList<>();
        for (String token : raw.split(SEPARATOR, -1)) {
            Optional<VoidRescueStep> step = VoidRescueStep.parse(token);
            if (step.isEmpty()) {
                return Optional.empty();
            }
            parsed.add(step.get());
        }
        return Optional.of(new VoidRescueChain(parsed));
    }

    /** The setting text this chain was parsed from. */
    public String encode() {
        return String.join(SEPARATOR, steps.stream().map(VoidRescueStep::encode).toList());
    }

    /** True when no step is configured, so the caller leaves the fall to vanilla. */
    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /** Walk the steps in order and return the first that resolves; empty means no rescue is possible. */
    public Optional<Position> resolve(Function<VoidRescueStep, Optional<Position>> resolver) {
        Objects.requireNonNull(resolver, "resolver");
        for (VoidRescueStep step : steps) {
            Optional<Position> resolved = resolver.apply(step);
            if (resolved.isPresent()) {
                return resolved;
            }
        }
        return Optional.empty();
    }
}
