package com.uxplima.uxmessentials.migration;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * What the importer does when an imported record collides with an existing uxmEssentials record.
 * The chosen branch is recorded per record in the audit {@code conflict} field.
 *
 * <ul>
 *   <li>{@link #SKIP} (default): keep the existing record; the safe, re-runnable choice.
 *   <li>{@link #OVERWRITE}. The source value replaces the existing one (balance still honours the
 *       separate {@code balance-policy}).
 *   <li>{@link #MERGE}: accepted so an operator file that names it keeps loading; every writer treats it as
 *       {@link #OVERWRITE}.
 * </ul>
 */
public enum ConflictPolicy {
    SKIP,
    OVERWRITE,
    MERGE;

    /** The conservative default: an existing record always wins, so a re-run can never clobber data. */
    public static ConflictPolicy defaultPolicy() {
        return SKIP;
    }

    /**
     * Parse an {@code on-conflict} value. Empty when the value is not one of {@code skip}, {@code overwrite} or
     * {@code merge}, so the caller can tell the operator instead of quietly picking a policy for them.
     */
    public static Optional<ConflictPolicy> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        return switch (raw.strip().toLowerCase(Locale.ROOT)) {
            case "skip" -> Optional.of(SKIP);
            case "overwrite" -> Optional.of(OVERWRITE);
            case "merge" -> Optional.of(MERGE);
            default -> Optional.empty();
        };
    }
}
