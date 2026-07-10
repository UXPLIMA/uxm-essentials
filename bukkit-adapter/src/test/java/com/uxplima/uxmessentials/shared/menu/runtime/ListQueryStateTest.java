package com.uxplima.uxmessentials.shared.menu.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ListQueryState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class ListQueryStateTest {

    private static final List<String> SORTS = List.of("name", "created", "rating");

    @Test
    void sortCyclesForwardWithWraparound() {
        ListQueryState state = new ListQueryState(SORTS);
        assertThat(state.sort()).isEqualTo("name");
        state.nextSort();
        assertThat(state.sort()).isEqualTo("created");
        state.nextSort();
        assertThat(state.sort()).isEqualTo("rating");
        state.nextSort();
        assertThat(state.sort()).isEqualTo("name");
    }

    @Test
    void sortCyclesBackwardWithWraparound() {
        ListQueryState state = new ListQueryState(SORTS);
        state.previousSort();
        assertThat(state.sort()).isEqualTo("rating");
        state.previousSort();
        assertThat(state.sort()).isEqualTo("created");
        state.previousSort();
        assertThat(state.sort()).isEqualTo("name");
    }

    @Test
    void noSortsReportsEmptyAndCyclingIsANoOp() {
        ListQueryState state = new ListQueryState(List.of());
        assertThat(state.sort()).isEmpty();
        state.nextSort();
        assertThat(state.sort()).isEmpty();
        state.previousSort();
        assertThat(state.sort()).isEmpty();
        state.resetSort();
        assertThat(state.sort()).isEmpty();
    }

    @Test
    void filterResetsPageToZero() {
        ListQueryState state = new ListQueryState(SORTS);
        state.page(7);
        state.filter("type", "shop");
        assertThat(state.page()).isZero();
    }

    @Test
    void clearFilterResetsPageToZero() {
        ListQueryState state = new ListQueryState(SORTS);
        state.filter("type", "shop");
        state.page(7);
        state.clearFilter("type");
        assertThat(state.page()).isZero();
    }

    @Test
    void nextSortResetsPageToZero() {
        ListQueryState state = new ListQueryState(SORTS);
        state.page(7);
        state.nextSort();
        assertThat(state.page()).isZero();
    }

    @Test
    void previousSortResetsPageToZero() {
        ListQueryState state = new ListQueryState(SORTS);
        state.page(7);
        state.previousSort();
        assertThat(state.page()).isZero();
    }

    @Test
    void resetSortResetsPageToZero() {
        ListQueryState state = new ListQueryState(SORTS);
        state.nextSort();
        state.page(7);
        state.resetSort();
        assertThat(state.page()).isZero();
        assertThat(state.sort()).isEqualTo("name");
    }

    @Test
    void emptyFilterValueClearsRatherThanFiltersOnEmptiness() {
        ListQueryState state = new ListQueryState(SORTS);
        state.filter("type", "shop");
        assertThat(state.filters()).containsEntry("type", "shop");
        state.filter("type", "");
        assertThat(state.filters()).doesNotContainKey("type");
    }

    @Test
    void filtersReadBackWhatWasSet() {
        ListQueryState state = new ListQueryState(SORTS);
        state.filter("type", "shop");
        state.filter("world", "world_nether");
        assertThat(state.filters()).containsOnly(entry("type", "shop"), entry("world", "world_nether"));
    }

    @Test
    void pageCountRoundsUpAndIsAtLeastOne() {
        ListQueryState state = new ListQueryState(SORTS);
        state.recordTotal(0);
        assertThat(state.pageCount(9)).isOne();
        state.recordTotal(9);
        assertThat(state.pageCount(9)).isEqualTo(1);
        state.recordTotal(10);
        assertThat(state.pageCount(9)).isEqualTo(2);
        state.recordTotal(19);
        assertThat(state.pageCount(9)).isEqualTo(3);
    }

    @Test
    void pageCountUsesRecordedTotalNotAnyList() {
        ListQueryState state = new ListQueryState(SORTS);
        state.recordTotal(100);
        assertThat(state.total()).isEqualTo(100);
        assertThat(state.pageCount(10)).isEqualTo(10);
    }

    @Test
    void requestCarriesPageSizeSortAndFilters() {
        ListQueryState state = new ListQueryState(SORTS);
        state.nextSort();
        state.filter("type", "shop");
        state.page(3);
        PageRequest request = state.request(45);
        assertThat(request.page()).isEqualTo(3);
        assertThat(request.size()).isEqualTo(45);
        assertThat(request.sort()).isEqualTo("created");
        assertThat(request.filters()).containsOnly(entry("type", "shop"));
    }

    @Test
    void queryStateReturnsSameInstancePerIdAndDifferentPerId() {
        MenuHolder holder = newHolder();
        ListQueryState first = holder.queryState("warps", SORTS);
        ListQueryState again = holder.queryState("warps", SORTS);
        ListQueryState other = holder.queryState("homes", SORTS);
        assertThat(again).isSameAs(first);
        assertThat(other).isNotSameAs(first);
    }

    private static MenuHolder newHolder() {
        MenuSpec spec = new MenuSpecLoader().parse("rows = 1\nitems {}");
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "P"), null, 0);
        return new MenuHolder("t", spec, ctx);
    }

    private static org.assertj.core.data.MapEntry<String, String> entry(String key, String value) {
        return org.assertj.core.data.MapEntry.entry(key, value);
    }
}
