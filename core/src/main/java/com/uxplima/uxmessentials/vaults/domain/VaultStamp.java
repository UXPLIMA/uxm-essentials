package com.uxplima.uxmessentials.vaults.domain;

import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Which stored row a copy of a vault was read from, and how many item writes that row had seen. It is how a server
 * tells a copy read before its own write from one read after, without comparing clocks: servers' clocks differ, but
 * the row's version only ever goes up, by one per item write, in the database itself.
 *
 * <p>The lineage names one row from its insert to its delete. A vault deleted and bought again is a new row with a new
 * lineage and a version counting from zero again, so versions are only compared within one lineage. A row from before
 * lineages were recorded has none; all such rows share the {@code null} lineage, which is safe because one owner's
 * index holds at most one row at a time and a new row always gets a lineage.
 *
 * @param lineage the row's lineage, or {@code null} for a row from before lineages were recorded
 * @param version the number of item writes the row had seen
 */
public record VaultStamp(@Nullable String lineage, long version) {

    /** The stamp of a copy that was never read from a row, and of a row from before stamps were recorded. */
    public static final VaultStamp UNRECORDED = new VaultStamp(null, 0L);

    public VaultStamp {
        if (version < 0) {
            throw new IllegalArgumentException("a vault version cannot be negative: " + version);
        }
    }

    /** The stamp of a row about to be inserted: a fresh lineage, no item writes yet. */
    public static VaultStamp newRow() {
        return new VaultStamp(UUID.randomUUID().toString(), 0L);
    }

    /** True when this copy and {@code other} were read from the same row. */
    public boolean sameRow(VaultStamp other) {
        return Objects.equals(lineage, other.lineage);
    }

    /** True when this copy was read from the same row as {@code other} before that row reached {@code other}. */
    public boolean before(VaultStamp other) {
        return sameRow(other) && version < other.version;
    }
}
