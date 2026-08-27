package com.uxplima.uxmessentials.customcommands.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * One declared positional argument: the name its parsed value is keyed under (reachable in the action chain as
 * {@code %arg_<name>%}), the kind that decides how it is read, and three modifiers. {@code optional} lets a
 * trailing argument be left off, {@code rest} makes a final text argument capture the remaining input including
 * spaces, and the numeric bounds turn an out-of-range number into a syntax error before any action runs.
 *
 * <p>The record validates only what is true of a single argument. Rules that involve the whole list (optional
 * arguments must be trailing, a rest capture must be last) belong to {@link ArgumentList}, because a file is
 * reported on as a whole rather than aborted at the first bad row.
 */
public record CommandArgument(
        String name, ArgumentKind kind, boolean optional, boolean rest, Optional<Double> min, Optional<Double> max) {

    public CommandArgument {
        name = Objects.requireNonNull(name, "name").strip();
        if (name.isBlank()) {
            throw new IllegalArgumentException("command argument name must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
    }

    /** A required, single-word argument with no numeric range: the plainest declaration a file can carry. */
    public static CommandArgument of(String name, ArgumentKind kind) {
        return new CommandArgument(name, kind, false, false, Optional.empty(), Optional.empty());
    }
}
