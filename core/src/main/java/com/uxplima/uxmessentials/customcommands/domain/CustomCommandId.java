package com.uxplima.uxmessentials.customcommands.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The stable identity of one operator-authored command: the name of its file under {@code commands/custom/}, and
 * the key an override is keyed against. Lowercase, no whitespace and no leading slash, because the same string is
 * both a file name and a catalog key.
 */
public record CustomCommandId(String value) {

    private static final Pattern SHAPE = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    public CustomCommandId {
        Objects.requireNonNull(value, "value");
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("custom command id must match [a-z0-9][a-z0-9_-]*: " + value);
        }
    }

    /** Normalise what an operator typed (trim, lowercase) and validate the result. */
    public static CustomCommandId of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new CustomCommandId(raw.strip().toLowerCase(Locale.ROOT));
    }

    /** Whether {@code raw} would be accepted, so a loader can skip a bad file name without catching. */
    public static boolean valid(@Nullable String raw) {
        return raw != null
                && SHAPE.matcher(raw.strip().toLowerCase(Locale.ROOT)).matches();
    }

    @Override
    public String toString() {
        return value;
    }
}
