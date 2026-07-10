package com.uxplima.uxmessentials.shared.menu.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import org.junit.jupiter.api.Test;

class PagedResultTest {

    @Test
    void pageCountRoundsUpAndIsAtLeastOne() {
        assertThat(PagedResult.of(List.of(), 0).pageCount(45)).isEqualTo(1);
        assertThat(PagedResult.of(List.of(), 45).pageCount(45)).isEqualTo(1);
        assertThat(PagedResult.of(List.of(), 46).pageCount(45)).isEqualTo(2);
        assertThat(PagedResult.of(List.of(), 100_000).pageCount(45)).isEqualTo(2223);
    }

    @Test
    void rowsAndPinnedAreDefensivelyCopied() {
        java.util.ArrayList<String> rows = new java.util.ArrayList<>(List.of("a"));
        PagedResult<String> result = PagedResult.of(rows, 1);
        rows.add("b");
        assertThat(result.rows()).containsExactly("a");
    }

    @Test
    void aNegativeTotalIsRejected() {
        assertThatThrownBy(() -> PagedResult.of(List.of(), -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPageRequestNormalisesItsFiltersAndRejectsANonPositiveSize() {
        PageRequest request = new PageRequest(0, 45, "RATING", Map.of("category", "shop"));
        assertThat(request.filters()).containsEntry("category", "shop");
        assertThatThrownBy(() -> new PageRequest(0, 0, "", Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageRequest(-1, 45, "", Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
