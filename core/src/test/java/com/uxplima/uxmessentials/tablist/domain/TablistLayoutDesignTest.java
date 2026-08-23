package com.uxplima.uxmessentials.tablist.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.tablist.domain.TablistLayout.Direction;
import org.junit.jupiter.api.Test;

class TablistLayoutDesignTest {

    @Test
    void acceptsUnambiguousFixedAndPlayerOwnedSlots() {
        TablistLayoutDesign design = new TablistLayoutDesign(
                "default",
                80,
                Direction.COLUMNS,
                20,
                List.of(fixed(1)),
                List.of(new TablistPlayerGroup("players", List.of(new TablistSlotRange(2, 60)))));

        assertThat(design.slotCount()).isEqualTo(80);
        assertThat(design.listOrder(1)).isEqualTo(Integer.MAX_VALUE - 1);
        assertThat(design.playerGroups()).extracting(TablistPlayerGroup::id).containsExactly("players");
    }

    @Test
    void rejectsEveryAmbiguousOwnershipShape() {
        assertThatThrownBy(() -> design(List.of(fixed(1), fixed(1)), List.of())).hasMessageContaining("owned by both");
        assertThatThrownBy(() -> design(
                        List.of(fixed(2)),
                        List.of(new TablistPlayerGroup("players", List.of(new TablistSlotRange(2, 3))))))
                .hasMessageContaining("owned by both");
        assertThatThrownBy(() -> design(
                        List.of(),
                        List.of(
                                new TablistPlayerGroup("staff", List.of(new TablistSlotRange(2, 4))),
                                new TablistPlayerGroup("players", List.of(new TablistSlotRange(4, 6))))))
                .hasMessageContaining("owned by both");
        assertThatThrownBy(() -> design(
                        List.of(),
                        List.of(
                                new TablistPlayerGroup("same", List.of(new TablistSlotRange(2, 4))),
                                new TablistPlayerGroup("same", List.of(new TablistSlotRange(8, 9))))))
                .hasMessageContaining("duplicate tablist player group id");
    }

    @Test
    void rejectsSlotsOutsideTheDeclaredGridAndImpossibleGridDimensions() {
        assertThatThrownBy(
                        () -> new TablistLayoutDesign("bad", 80, Direction.COLUMNS, 20, List.of(fixed(81)), List.of()))
                .hasMessageContaining("outside");
        assertThatThrownBy(() -> new TablistLayoutDesign("bad", 80, Direction.COLUMNS, 10, List.of(), List.of()))
                .hasMessageContaining("cannot hold");
        assertThatThrownBy(() -> new TablistLayoutDesign("bad", 81, Direction.COLUMNS, 21, List.of(), List.of()))
                .hasMessageContaining("between 1 and 80");
    }

    private static TablistLayoutDesign design(List<TablistFiller> fixed, List<TablistPlayerGroup> groups) {
        return new TablistLayoutDesign("test", 80, Direction.COLUMNS, 20, fixed, groups);
    }

    private static TablistFiller fixed(int slot) {
        return new TablistFiller(slot, "slot " + slot, Optional.empty());
    }
}
