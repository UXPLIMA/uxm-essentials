package com.uxplima.uxmessentials.tablist.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.tablist.domain.TablistLayout.Direction;
import org.junit.jupiter.api.Test;

class VirtualTabPlannerTest {

    @Test
    void materializesEveryUnclaimedSlotAsAnExplicitEmptyCell() {
        TablistLayoutDesign design = design(8, List.of(fixed(2)), List.of());

        VirtualTabGrid<String> grid = VirtualTabPlanner.plan(design, Map.of(), value -> value);

        assertThat(grid.slotCount()).isEqualTo(8);
        assertThat(grid.cells()).extracting(VirtualTabCell::slot).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(grid.cell(1).content()).isInstanceOf(VirtualTabCell.Empty.class);
        assertThat(grid.cell(2).content()).isInstanceOf(VirtualTabCell.Fixed.class);
        assertThat(grid.cell(8).content()).isInstanceOf(VirtualTabCell.Empty.class);
    }

    @Test
    void placesAlreadySortedOccupantsIntoAuthoredGroupSlotOrder() {
        TablistPlayerGroup players =
                new TablistPlayerGroup("players", List.of(new TablistSlotRange(3, 4), new TablistSlotRange(7, 7)));
        TablistLayoutDesign design = design(8, List.of(fixed(1)), List.of(players));

        VirtualTabGrid<String> grid =
                VirtualTabPlanner.plan(design, Map.of("players", List.of("Alice", "Bob", "Cara")), value -> value);

        assertPlayer(grid.cell(3), "players", "Alice");
        assertPlayer(grid.cell(4), "players", "Bob");
        assertPlayer(grid.cell(7), "players", "Cara");
        assertThat(grid.cell(2).content()).isInstanceOf(VirtualTabCell.Empty.class);
        assertThat(grid.overflowByGroup()).isEmpty();
    }

    @Test
    void firstMatchingGroupOwnsAPlayerAndLaterGroupsCannotDuplicateThem() {
        TablistLayoutDesign design = design(
                8,
                List.of(),
                List.of(
                        new TablistPlayerGroup("staff", List.of(new TablistSlotRange(1, 2))),
                        new TablistPlayerGroup("players", List.of(new TablistSlotRange(3, 5)))));

        VirtualTabGrid<Roster> grid = VirtualTabPlanner.plan(
                design,
                Map.of(
                        "staff", List.of(new Roster(1, "Owner")),
                        "players", List.of(new Roster(1, "Owner"), new Roster(2, "Alex"))),
                Roster::id);

        assertRoster(grid.cell(1), "staff", 1);
        assertRoster(grid.cell(3), "players", 2);
        assertThat(grid.cell(4).content()).isInstanceOf(VirtualTabCell.Empty.class);
    }

    @Test
    void reportsUniqueUnassignedOccupantsAsPerGroupOverflow() {
        TablistPlayerGroup players = new TablistPlayerGroup("players", List.of(new TablistSlotRange(1, 2)));
        TablistLayoutDesign design = design(4, List.of(), List.of(players));

        VirtualTabGrid<Roster> grid = VirtualTabPlanner.plan(
                design,
                Map.of(
                        "players",
                        List.of(
                                new Roster(1, "A"),
                                new Roster(2, "B"),
                                new Roster(3, "C"),
                                new Roster(3, "C duplicate"),
                                new Roster(4, "D"))),
                Roster::id);

        assertThat(grid.overflowByGroup()).containsEntry("players", 2);
    }

    @SuppressWarnings("unchecked")
    private static void assertPlayer(VirtualTabCell<String> cell, String group, String occupant) {
        VirtualTabCell.Player<String> player = (VirtualTabCell.Player<String>) cell.content();
        assertThat(player.groupId()).isEqualTo(group);
        assertThat(player.occupant()).isEqualTo(occupant);
    }

    @SuppressWarnings("unchecked")
    private static void assertRoster(VirtualTabCell<Roster> cell, String group, int id) {
        VirtualTabCell.Player<Roster> player = (VirtualTabCell.Player<Roster>) cell.content();
        assertThat(player.groupId()).isEqualTo(group);
        assertThat(player.occupant().id()).isEqualTo(id);
    }

    private static TablistLayoutDesign design(int slots, List<TablistFiller> fixed, List<TablistPlayerGroup> groups) {
        return new TablistLayoutDesign("test", slots, Direction.COLUMNS, 20, fixed, groups);
    }

    private static TablistFiller fixed(int slot) {
        return new TablistFiller(slot, "fixed " + slot, Optional.empty());
    }

    private record Roster(int id, String name) {}
}
