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
 *
 * <p>A command that is not one of the plugin's own carries a namespace in front of it, written
 * {@code <namespace>:<id>} ({@code custom:welcome} for an operator's own definition out of
 * {@code commands/custom/}). The namespace keeps a definition somebody wrote from colliding with a built-in of the
 * same name in {@code commands.conf}, and the part after the colon takes the wider shape those ids allow: digits may
 * lead, and hyphens and underscores are legal, because the id is a file name the operator chose.
 */
public record CommandId(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9]*(:[a-z0-9][a-z0-9_-]*)?");

    public CommandId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "commandId must be lowercase letters/digits starting with a letter, optionally"
                            + " namespaced as <namespace>:<id>: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
