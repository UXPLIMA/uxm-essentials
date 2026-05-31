package com.uxplima.uxmessentials.migration.config;

import org.jspecify.annotations.NullMarked;

/**
 * The outcome of running one config file through the {@link ConfigVersionLadder}: the version it started
 * at and the version it ended at. {@code from == to} means the file was already current and the ladder
 * rewrote nothing (the common no-op path), which the caller uses to skip the backup and the disk write
 * entirely (docs/12-migration §12.2).
 *
 * @param from the on-disk {@code config-version} before the ladder ran
 * @param to the {@code config-version} after the ladder ran
 */
@NullMarked
public record MigrationResult(int from, int to) {

    /** True when the ladder advanced the file at least one step. */
    public boolean upgraded() {
        return to > from;
    }
}
