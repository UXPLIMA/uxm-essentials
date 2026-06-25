package com.uxplima.uxmessentials.shared.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Pagination;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PinnedEntry;
import org.junit.jupiter.api.Test;

class PaginationTest {

    @Test
    void splitsEntriesAcrossPages() {
        var slots = List.of(0, 1, 2);
        var p0 = Pagination.paginate(List.of("a", "b", "c", "d"), slots, 0);
        assertThat(p0.pageCount()).isEqualTo(2);
        assertThat(p0.placements()).containsExactly(Map.entry(0, "a"), Map.entry(1, "b"), Map.entry(2, "c"));
        var p1 = Pagination.paginate(List.of("a", "b", "c", "d"), slots, 1);
        assertThat(p1.placements()).containsExactly(Map.entry(0, "d"));
    }

    @Test
    void clampsOutOfRangePage() {
        assertThat(Pagination.paginate(List.of("a"), List.of(0, 1), 9).page()).isZero();
    }

    /** A test entry that opts into a fixed slot. A negative slot signals "out of range" for these tests. */
    private record Pin(String id, int pinnedSlot) implements PinnedEntry {}

    @Test
    void pinnedEntryRepeatsOnEveryPageWhileFlowAdvances() {
        var slots = List.of(0, 1, 2);
        var entries = List.of(new Pin("p", 1), "a", "b", "c", "d");
        var p0 = Pagination.paginate(entries, slots, 0);
        // Pin holds slot 1 on both pages; the flow uses the remaining slots 0 and 2.
        assertThat(p0.pageCount()).isEqualTo(2);
        assertThat(p0.placements())
                .containsExactly(Map.entry(1, new Pin("p", 1)), Map.entry(0, "a"), Map.entry(2, "b"));
        var p1 = Pagination.paginate(entries, slots, 1);
        assertThat(p1.placements())
                .containsExactly(Map.entry(1, new Pin("p", 1)), Map.entry(0, "c"), Map.entry(2, "d"));
    }

    @Test
    void pinnedSlotOutOfRangeFlowsLikeANormalEntry() {
        var slots = List.of(0, 1, 2);
        var entries = List.of(new Pin("p", 9), "a", "b");
        var p0 = Pagination.paginate(entries, slots, 0);
        // Slot 9 is not a content slot, so the pin flows from the front like any other entry.
        assertThat(p0.pageCount()).isEqualTo(1);
        assertThat(p0.placements())
                .containsExactly(Map.entry(0, new Pin("p", 9)), Map.entry(1, "a"), Map.entry(2, "b"));
    }

    @Test
    void firstPinWinsAColludingSlotAndTheSecondFlows() {
        var slots = List.of(0, 1, 2);
        var first = new Pin("first", 1);
        var second = new Pin("second", 1);
        var entries = List.of(first, second, "a");
        var p0 = Pagination.paginate(entries, slots, 0);
        // First claims slot 1; second loses the collision and flows across the remaining slots 0 and 2.
        assertThat(p0.pageCount()).isEqualTo(1);
        assertThat(p0.placements()).containsExactly(Map.entry(1, first), Map.entry(0, second), Map.entry(2, "a"));
    }

    @Test
    void everyContentSlotPinnedLeavesNoFlowSlotsAndOnePage() {
        var slots = List.of(0, 1);
        var p0 = new Pin("p0", 0);
        var p1 = new Pin("p1", 1);
        var entries = List.of(p0, p1, "a", "b");
        var page = Pagination.paginate(entries, slots, 0);
        // No flow slots remain, so the flowing entries can't place and there is a single page of only pins.
        assertThat(page.pageCount()).isEqualTo(1);
        assertThat(page.placements()).containsExactly(Map.entry(0, p0), Map.entry(1, p1));
    }
}
