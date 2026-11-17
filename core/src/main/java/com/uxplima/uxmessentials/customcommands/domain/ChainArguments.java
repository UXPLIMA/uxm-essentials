package com.uxplima.uxmessentials.customcommands.domain;

import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.customcommands.domain.CommandLineArguments.Line;

/**
 * The values a run hands its action chain. {@code typed} is every argument as the player typed it, for actions that
 * show it as text. {@code forPlayerLines} and {@code forPrivilegedLines} hold only the values that may go into what the
 * player runs or says as themselves, or into every other action (see {@link CommandLineArguments}); those actions are
 * never given anything else.
 */
public record ChainArguments(
        Map<String, String> typed, Map<String, String> forPlayerLines, Map<String, String> forPrivilegedLines) {

    public ChainArguments {
        typed = Map.copyOf(Objects.requireNonNull(typed, "typed"));
        forPlayerLines = Map.copyOf(Objects.requireNonNull(forPlayerLines, "forPlayerLines"));
        forPrivilegedLines = Map.copyOf(Objects.requireNonNull(forPrivilegedLines, "forPrivilegedLines"));
    }

    /** {@code typed} for a run of {@code command}, with its actions limited to what its arguments allow there. */
    public static ChainArguments of(CustomCommand command, Map<String, String> typed) {
        return new ChainArguments(
                typed,
                CommandLineArguments.fitFor(Line.PLAYER, command, typed),
                CommandLineArguments.fitFor(Line.PRIVILEGED, command, typed));
    }

    /** The values an action acted on by {@code line} may use. */
    public Map<String, String> forLine(Line line) {
        return switch (line) {
            case PLAYER -> forPlayerLines;
            case PRIVILEGED -> forPrivilegedLines;
        };
    }
}
