package com.uxplima.uxmessentials.migration;

import org.jspecify.annotations.NullMarked;

/**
 * The pre-import backup seam (docs/12-migration §9, step 1; §3). Before a live run writes anything, the
 * importer takes a snapshot through this port and records its name on the {@code migration_import_start}
 * audit line, so a bad import is always recoverable. A dry run takes no snapshot (it writes nothing). The
 * bukkit-side impl snapshots the plugin data/config directory; the contract here stays platform-neutral.
 */
@NullMarked
@FunctionalInterface
public interface BackupSnapshot {

    /** Take a pre-import snapshot and return its name (for the audit {@code backup} field). */
    String take();
}
