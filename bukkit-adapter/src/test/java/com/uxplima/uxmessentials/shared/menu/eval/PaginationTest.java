package com.uxplima.uxmessentials.shared.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Pagination;
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
}
