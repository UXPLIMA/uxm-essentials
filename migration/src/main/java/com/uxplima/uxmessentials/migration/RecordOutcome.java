package com.uxplima.uxmessentials.migration;

import java.util.Locale;

/**
 * The per-record result the writer reports back for audit and summary counting (docs/12-migration §6,
 * §9). The {@code conflict} branch a record took is the audit {@code conflict} field; whether it landed
 * is the {@code ok} field.
 */
public enum RecordOutcome {

    /** The record was written (or, in dry-run, would have been). */
    WRITTEN,

    /** A collision was resolved by keeping the existing record; nothing was written. */
    SKIPPED,

    /** The existing record was replaced by the source value. */
    OVERWRITTEN,

    /** The source contributed only the parts the target lacked. */
    MERGED,

    /** The record could not be written; it is counted in {@code failed} and the run continues. */
    FAILED;

    /** True when the record landed in the target (live) or would have (dry-run). */
    public boolean ok() {
        return this != FAILED;
    }

    /** The lowercase audit token for the {@code conflict} field. */
    public String token() {
        return name().toLowerCase(Locale.ROOT);
    }
}
