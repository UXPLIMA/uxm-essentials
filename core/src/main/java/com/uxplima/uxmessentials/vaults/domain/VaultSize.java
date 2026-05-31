package com.uxplima.uxmessentials.vaults.domain;

/**
 * A vault's height in rows, the second of the two numbered quota families
 * ({@code uxmessentials.vault.size.<rows>}). A double-chest is the widest a vanilla inventory GUI renders, so
 * a vault is between one and six rows of nine slots; the bounds are enforced here so a quota node or a config
 * default that resolves outside the range is clamped before it reaches the aggregate or a created inventory.
 *
 * @param rows the number of nine-slot rows, in {@code [MIN_ROWS, MAX_ROWS]}
 */
public record VaultSize(int rows) {

    /** The smallest vault: a single nine-slot row. */
    public static final int MIN_ROWS = 1;

    /** The largest vault: a double-chest's six rows, the widest a chest GUI renders. */
    public static final int MAX_ROWS = 6;

    /** Slots in one row of a chest-style inventory. */
    public static final int SLOTS_PER_ROW = 9;

    public VaultSize {
        if (rows < MIN_ROWS || rows > MAX_ROWS) {
            throw new IllegalArgumentException("vault rows must be in [" + MIN_ROWS + ", " + MAX_ROWS + "]: " + rows);
        }
    }

    /** A size of {@code rows}, clamped into the renderable {@code [MIN_ROWS, MAX_ROWS]} range. */
    public static VaultSize ofClamped(long rows) {
        long clamped = Math.max(MIN_ROWS, Math.min(MAX_ROWS, rows));
        return new VaultSize((int) clamped);
    }

    /** The total slot count of a vault this many rows tall. */
    public int slots() {
        return rows * SLOTS_PER_ROW;
    }
}
