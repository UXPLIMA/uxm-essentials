package com.uxplima.uxmessentials.tablist.domain;

import java.util.List;
import java.util.Objects;

/**
 * A fixed-slot tab-list layout a {@link TablistFormat} may carry: the operator-authored {@link TablistFiller} entries and
 * the {@link Direction} that maps a 1-based slot to a grid cell. The vanilla client lays the tab list out as four columns
 * of {@code gridRows} cells (twenty on a standard 80-slot list); a {@link Direction#COLUMNS column} layout fills it
 * column by column (slot order = visual order), a {@link Direction#ROWS row} layout fills it left to right, row by row.
 *
 * <p>The layout positions entries through the client's <em>list-order</em> sort key (Paper 1.21.2+): a higher order sorts
 * a row nearer the top. {@link #slotToListOrder} turns a slot into that key so slot {@code 1} sorts highest among the
 * fillers and the last slot lowest. {@link #realPlayerOrder()} sits above every filler order so the real players occupy
 * the early slots and the fillers fill the cells after them. This is pure arithmetic — no Bukkit, no packets.
 *
 * <p>An {@link #empty()} layout carries no fillers and renders inert: a format with no layout behaves exactly as it did
 * before fillers existed (the native/skin name/order path, untouched). The fillers are held in the order the codec hands
 * them over; the adapter keys its per-(viewer, slot) tracking on {@link TablistFiller#slot()}, so order is presentational
 * only.
 *
 * @param fillers the filler entries this layout paints, in codec order (defensively copied)
 * @param direction how a 1-based slot maps to a grid cell
 * @param gridRows the number of rows per column the client renders (twenty for a standard 80-slot tab list)
 */
public record TablistLayout(List<TablistFiller> fillers, Direction direction, int gridRows) {

    /** The vanilla tab list is four columns wide; the slot arithmetic mirrors the client's fill order. */
    public static final int COLUMNS = 4;

    /** The standard tab list is four columns of twenty cells — eighty slots. */
    public static final int DEFAULT_GRID_ROWS = 20;

    /**
     * The list-order key real players are given so they sort above every filler. A filler's order is
     * {@code REAL_PLAYER_ORDER - translateSlot(slot)} (see {@link #slotToListOrder}), which for any positive slot is
     * strictly below this, so the real players always occupy the early slots and the fillers the cells after them.
     */
    public static final int REAL_PLAYER_ORDER = Integer.MAX_VALUE;

    /** How a 1-based slot maps to a grid cell: down the columns first, or across the rows first. */
    public enum Direction {
        /** Fill column by column: slot {@code 1} is the first cell of the first column. Slot order = visual order. */
        COLUMNS,
        /** Fill row by row, left to right: slot {@code 1} is the first cell of the first row across all columns. */
        ROWS
    }

    public TablistLayout {
        Objects.requireNonNull(fillers, "fillers");
        Objects.requireNonNull(direction, "direction");
        if (gridRows <= 0) {
            throw new IllegalArgumentException("a tablist layout grid must have a positive row count, got " + gridRows);
        }
        fillers = List.copyOf(fillers);
    }

    /** The inert layout a format with no fillers carries: no rows painted, the native name/order path untouched. */
    public static TablistLayout empty() {
        return new TablistLayout(List.of(), Direction.COLUMNS, DEFAULT_GRID_ROWS);
    }

    /** True when this layout paints no filler rows — the format renders exactly as it did before fillers existed. */
    public boolean isEmpty() {
        return fillers.isEmpty();
    }

    /**
     * The client list-order key a filler {@code slot} maps to, given a fill {@code direction} and {@code gridRows} per
     * column. The slot is first translated to the cell index the client renders it in, then subtracted from
     * {@link #REAL_PLAYER_ORDER} so that slot {@code 1} sorts highest (just below the real players) and the last slot
     * lowest. The translation mirrors the vanilla fill: a {@link Direction#COLUMNS column} layout keeps the slot as-is
     * (the client already fills column by column), a {@link Direction#ROWS row} layout maps slot to its row-major cell.
     */
    public static int slotToListOrder(int slot, Direction direction, int gridRows) {
        Objects.requireNonNull(direction, "direction");
        if (slot <= 0) {
            throw new IllegalArgumentException("a tablist slot must be strictly positive, got " + slot);
        }
        if (gridRows <= 0) {
            throw new IllegalArgumentException("a tablist grid must have a positive row count, got " + gridRows);
        }
        return REAL_PLAYER_ORDER - translateSlot(slot, direction, gridRows);
    }

    /**
     * Translate a 1-based {@code slot} to the 1-based cell index the client renders it in. {@link Direction#COLUMNS} is
     * the identity (the vanilla list already fills column by column), while {@link Direction#ROWS} maps a slot authored
     * row by row onto the column-major cell the client expects: {@code (slot-1) % COLUMNS * gridRows + (slot-1) / COLUMNS
     * + 1}. Mirrors the standard tab-list layout arithmetic so a row-fill places entries identically.
     */
    private static int translateSlot(int slot, Direction direction, int gridRows) {
        return switch (direction) {
            case COLUMNS -> slot;
            case ROWS -> (slot - 1) % COLUMNS * gridRows + (slot - 1) / COLUMNS + 1;
        };
    }

    /** The list-order key the real players sit at, above every filler, so they take the early slots. */
    public int realPlayerOrder() {
        return REAL_PLAYER_ORDER;
    }
}
