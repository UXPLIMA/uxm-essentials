package com.uxplima.uxmessentials.migration;

import java.nio.file.Path;
import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * The settled inputs for one import run: where to read the source from, whether it is a real or a
 * dry-run write, and the two conflict knobs (docs/12-migration §6). An {@link ImportOptions} is built
 * once from {@code migration.conf} plus the command's {@code --dry-run} flag and threaded read-only
 * through the whole pipeline, so the plan, the mappers, and the writer all see the same decisions.
 *
 * @param sourcePath the directory (or, for a future DB-backed source, the location) to read
 * @param mode live write versus dry-run accumulate
 * @param onConflict how a collision with an existing record is resolved
 * @param balancePolicy whether an existing wallet balance is overwritten
 */
@NullMarked
public record ImportOptions(Path sourcePath, ImportMode mode, ConflictPolicy onConflict, BalancePolicy balancePolicy) {

    public ImportOptions {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(onConflict, "onConflict");
        Objects.requireNonNull(balancePolicy, "balancePolicy");
    }

    /** Live-write options with the safe default conflict and balance policies. */
    public static ImportOptions live(Path sourcePath) {
        return new ImportOptions(
                sourcePath, ImportMode.LIVE, ConflictPolicy.defaultPolicy(), BalancePolicy.defaultPolicy());
    }

    /** A copy of these options switched to dry-run; every other decision is preserved. */
    public ImportOptions asDryRun() {
        return new ImportOptions(sourcePath, ImportMode.DRY_RUN, onConflict, balancePolicy);
    }

    /** True when this run only reports what it would write. */
    public boolean isDryRun() {
        return mode.isDryRun();
    }
}
