package com.uxplima.uxmessentials.tablist.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.tablist.domain.TablistLayout.Direction;
import org.junit.jupiter.api.Test;

class TablistLayoutTest {

    @Test
    void emptyLayoutPaintsNothing() {
        TablistLayout layout = TablistLayout.empty();
        assertThat(layout.isEmpty()).isTrue();
        assertThat(layout.fillers()).isEmpty();
        assertThat(layout.direction()).isEqualTo(Direction.COLUMNS);
        assertThat(layout.gridRows()).isEqualTo(TablistLayout.DEFAULT_GRID_ROWS);
        assertThat(layout.exact()).isFalse();
    }

    @Test
    void anExactLayoutIsActiveEvenWithoutAuthoredFixedSlots() {
        TablistLayout layout = new TablistLayout(List.of(), Direction.COLUMNS, 20, true);

        assertThat(layout.isEmpty()).isFalse();
        assertThat(layout.exact()).isTrue();
        assertThat(layout.capacity()).isEqualTo(80);
    }

    @Test
    void carriesItsFillers() {
        TablistFiller a = new TablistFiller(1, "<gold>One", Optional.empty());
        TablistFiller b = new TablistFiller(5, "<gray>Two", Optional.empty());
        TablistLayout layout = new TablistLayout(List.of(a, b), Direction.COLUMNS, 20);

        assertThat(layout.isEmpty()).isFalse();
        assertThat(layout.fillers()).containsExactly(a, b);
    }

    @Test
    void columnsKeepsSlotOrderSoSlotOneSortsHighest() {
        // Column fill: slot order is the visual order, so a lower slot must get a HIGHER list order (sorts nearer top).
        int first = TablistLayout.slotToListOrder(1, Direction.COLUMNS, 20);
        int second = TablistLayout.slotToListOrder(2, Direction.COLUMNS, 20);
        int last = TablistLayout.slotToListOrder(80, Direction.COLUMNS, 20);

        assertThat(first).isGreaterThan(second);
        assertThat(second).isGreaterThan(last);
        // Every filler sits strictly below the real players' order, so real players take the early slots.
        assertThat(first).isLessThan(TablistLayout.REAL_PLAYER_ORDER);
        assertThat(first).isEqualTo(Integer.MAX_VALUE - 1);
        assertThat(last).isEqualTo(Integer.MAX_VALUE - 80);
    }

    @Test
    void rowsMapSlotToTheRowMajorCell() {
        // Row fill, 4 columns x 20 rows: slot 1 -> cell 1 (top-left), slot 2 -> cell 21 (second column, top), slot 5 ->
        // cell 2 (first column, second row). The list order is MAX - cell, so the same cell yields the same order.
        assertThat(TablistLayout.slotToListOrder(1, Direction.ROWS, 20)).isEqualTo(Integer.MAX_VALUE - 1);
        assertThat(TablistLayout.slotToListOrder(2, Direction.ROWS, 20)).isEqualTo(Integer.MAX_VALUE - 21);
        assertThat(TablistLayout.slotToListOrder(5, Direction.ROWS, 20)).isEqualTo(Integer.MAX_VALUE - 2);
    }

    @Test
    void realPlayerOrderSitsAboveEveryFiller() {
        assertThat(TablistLayout.empty().realPlayerOrder()).isEqualTo(TablistLayout.REAL_PLAYER_ORDER);
        assertThat(TablistLayout.slotToListOrder(1, Direction.COLUMNS, 20))
                .isLessThan(TablistLayout.empty().realPlayerOrder());
    }

    @Test
    void rejectsANonPositiveSlotOrGrid() {
        assertThatThrownBy(() -> TablistLayout.slotToListOrder(0, Direction.COLUMNS, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TablistLayout.slotToListOrder(1, Direction.COLUMNS, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TablistLayout(List.of(), Direction.COLUMNS, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
