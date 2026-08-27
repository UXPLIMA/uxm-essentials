package com.uxplima.uxmessentials.customcommands.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The rules an argument list obeys as a whole, reported rather than thrown so a loader can name every problem in
 * one pass and skip the file once, instead of dripping one error per reload.
 *
 * <p>Three of the four rules exist because Brigadier cannot express the alternative: only a trailing run of
 * arguments may be optional (a node chain has one path), only the last argument may capture the rest of the input,
 * and a bound belongs to a number. The fourth, unique names, exists because the parsed values are keyed by name.
 */
public final class ArgumentList {

    private ArgumentList() {}

    /** Every problem with {@code arguments}, in declaration order; empty when the list is valid. */
    public static List<String> validate(List<CommandArgument> arguments) {
        Objects.requireNonNull(arguments, "arguments");
        List<String> problems = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean sawOptional = false;
        for (int i = 0; i < arguments.size(); i++) {
            CommandArgument argument = arguments.get(i);
            if (!seen.add(argument.name().toLowerCase(Locale.ROOT))) {
                problems.add("argument '" + argument.name() + "' is declared twice");
            }
            if (sawOptional && !argument.optional()) {
                problems.add("argument '" + argument.name() + "' is required but follows an optional argument");
            }
            sawOptional = sawOptional || argument.optional();
            if (argument.rest() && i != arguments.size() - 1) {
                problems.add("argument '" + argument.name() + "' captures the rest of the input but is not last");
            }
            problems.addAll(boundProblems(argument));
        }
        return List.copyOf(problems);
    }

    private static List<String> boundProblems(CommandArgument argument) {
        if (argument.min().isEmpty() && argument.max().isEmpty()) {
            return List.of();
        }
        if (!argument.kind().numeric()) {
            return List.of("argument '" + argument.name() + "' has a numeric range but is not a number");
        }
        if (argument.min().isPresent()
                && argument.max().isPresent()
                && argument.min().get() > argument.max().get()) {
            return List.of("argument '" + argument.name() + "' has a min greater than its max");
        }
        return List.of();
    }
}
