package com.uxplima.uxmessentials.tablist.domain;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.display.DisplayCondition;

/**
 * One roster group in an exact {@link TablistLayout}: the cells a live player is drawn into, who may be drawn there, and
 * how that row reads. A layout with no group draws no player at all when the format suppresses the real ones, which is
 * the fully synthetic grid; a layout with a group gets the players back, in the cells the operator chose rather than the
 * cells the client would have given them.
 *
 * <p>The {@code condition} is evaluated against the <em>candidate</em>, not the viewer, so a group may hold the staff, a
 * world, or everybody. The {@code text} is the row of one candidate, rendered with that candidate as the subject: the
 * {@code {player}} token is their name and a PlaceholderAPI placeholder reads their state. A candidate matching two
 * groups is drawn once, in the first group that has a free cell (see {@link VirtualTabPlanner}).
 *
 * @param id the group name, non-blank; the planner reports overflow under it and the codec rejects a repeat
 * @param ranges the cells this group owns, in the order they are filled
 * @param condition who may be drawn here; {@link DisplayCondition#always()} for everybody
 * @param text the raw MiniMessage source of one occupant's row, rendered with the occupant as the subject
 */
public record TablistRosterGroup(String id, List<TablistSlotRange> ranges, DisplayCondition condition, String text) {

    public TablistRosterGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(ranges, "ranges");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(text, "text");
        if (id.isBlank()) {
            throw new IllegalArgumentException("a tablist roster group id cannot be blank");
        }
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("tablist roster group '" + id + "' must own at least one slot range");
        }
        ranges = List.copyOf(ranges);
    }

    /** This group as the planner's placement value: the same id and ranges, without the condition or the text. */
    public TablistPlayerGroup placement() {
        return new TablistPlayerGroup(id, ranges);
    }

    /** Every cell this group owns, in fill order. */
    public List<Integer> slots() {
        return placement().slots();
    }
}
