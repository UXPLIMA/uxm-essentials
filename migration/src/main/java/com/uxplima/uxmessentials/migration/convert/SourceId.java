package com.uxplima.uxmessentials.migration.convert;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.jspecify.annotations.NullMarked;

/**
 * Stable lowercase identifier for one legacy source: {@code "essentialsx"}, {@code "cmi"}, and so on.
 *
 * <p>The value is what an operator types on the command line ({@code /uxmess import essentialsx}),
 * the registry key the command resolves a {@link Convert} from, and the {@code source=} field on every
 * {@code migration_import_*} audit line. It is constrained to the same lowercase, no-spaces shape a
 * config key and a Java package segment allow, so the same string is safe everywhere it travels.
 *
 * @param value the canonical lowercase identifier
 */
@NullMarked
public record SourceId(String value) {

    private static final Pattern VALID = Pattern.compile("[a-z][a-z0-9]*");

    public SourceId {
        Objects.requireNonNull(value, "value");
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "source id must be lowercase letters/digits, starting with a letter: " + value);
        }
    }

    /** Build a source id from raw input, trimming and lower-casing it; rejects an invalid shape. */
    public static SourceId of(String raw) {
        Objects.requireNonNull(raw, "raw");
        return new SourceId(raw.strip().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
