package com.uxplima.uxmessentials.shared.application.command;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Stable identifier for one command, independent of its operator-configurable name.
 *
 * <p>The value is the command's default English literal ({@code "home"}, {@code "tpa"}) and is the
 * single source of truth for config keying and the permission node. It never changes when an operator
 * renames the command in {@code commands.conf}, which is what keeps permissions stable across
 * a rename. Constrained to the same lowercase, no-spaces shape a command literal allows.
 */
public record CommandId(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9]*");

    public CommandId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "commandId must be lowercase letters/digits, starting with a letter: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
