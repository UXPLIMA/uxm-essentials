package com.uxplima.uxmessentials.holograms.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.junit.jupiter.api.Test;

/** The hologram click-action chain: the ordered {@link ClickAction} list carried on the {@link Hologram} aggregate. */
class HologramActionsTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position AT = Position.of(WORLD, 0, 64, 0);
    private static final Instant CREATED = Instant.EPOCH;

    private static Hologram fresh() {
        return Hologram.create(HologramName.of("a"), AT, List.of(new HologramLine("x")), CREATED);
    }

    private static ClickAction action(String value) {
        return new ClickAction(ClickTrigger.RIGHT_CLICK, ClickActionType.MESSAGE, value);
    }

    @Test
    void aFreshHologramHasNoActions() {
        Hologram hologram = fresh();

        assertThat(hologram.actions()).isEmpty();
        assertThat(hologram.hasActions()).isFalse();
    }

    @Test
    void appendsAndRemovesActions() {
        Hologram hologram = fresh();
        ClickAction act = action("hi");

        Hologram withAct = hologram.withActionAdded(act);

        assertThat(withAct.actions()).containsExactly(act);
        assertThat(withAct.hasActions()).isTrue();
        assertThat(withAct.withActionRemovedAt(0).actions()).isEmpty();
    }

    @Test
    void withActionAddedAppendsInOrderKeepingTheRest() {
        ClickAction first = action("first");
        ClickAction second = action("second");

        Hologram hologram = fresh().withActionAdded(first).withActionAdded(second);

        assertThat(hologram.actions()).containsExactly(first, second);
    }

    @Test
    void withActionRemovedAtDropsTheChosenOne() {
        ClickAction first = action("first");
        ClickAction second = action("second");
        Hologram hologram = fresh().withActionAdded(first).withActionAdded(second);

        assertThat(hologram.withActionRemovedAt(0).actions()).containsExactly(second);
        assertThat(hologram.withActionRemovedAt(1).actions()).containsExactly(first);
    }

    @Test
    void withActionRemovedAtRejectsAnOutOfRangeIndex() {
        Hologram hologram = fresh().withActionAdded(action("hi"));

        assertThatThrownBy(() -> hologram.withActionRemovedAt(1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> hologram.withActionRemovedAt(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void withActionInsertedAtPlacesTheActionAtTheIndex() {
        ClickAction first = action("first");
        ClickAction second = action("second");
        ClickAction wedged = action("wedged");
        Hologram hologram = fresh().withActionAdded(first).withActionAdded(second);

        assertThat(hologram.withActionInsertedAt(1, wedged).actions()).containsExactly(first, wedged, second);
        assertThat(hologram.withActionInsertedAt(0, wedged).actions()).containsExactly(wedged, first, second);
        // An index at the current size appends.
        assertThat(hologram.withActionInsertedAt(2, wedged).actions()).containsExactly(first, second, wedged);
    }

    @Test
    void withActionInsertedAtRejectsAnOutOfRangeIndex() {
        Hologram hologram = fresh().withActionAdded(action("hi"));

        assertThatThrownBy(() -> hologram.withActionInsertedAt(2, action("x")))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> hologram.withActionInsertedAt(-1, action("x")))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void withActionSetAtReplacesTheActionAtTheIndex() {
        ClickAction first = action("first");
        ClickAction second = action("second");
        ClickAction replacement = action("replacement");
        Hologram hologram = fresh().withActionAdded(first).withActionAdded(second);

        assertThat(hologram.withActionSetAt(0, replacement).actions()).containsExactly(replacement, second);
        assertThat(hologram.withActionSetAt(1, replacement).actions()).containsExactly(first, replacement);
    }

    @Test
    void withActionSetAtRejectsAnOutOfRangeIndex() {
        Hologram hologram = fresh().withActionAdded(action("hi"));

        assertThatThrownBy(() -> hologram.withActionSetAt(1, action("x")))
                .isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> hologram.withActionSetAt(-1, action("x")))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void withActionMovedReordersTheList() {
        ClickAction a = action("a");
        ClickAction b = action("b");
        ClickAction c = action("c");
        Hologram hologram = fresh().withActionAdded(a).withActionAdded(b).withActionAdded(c);

        assertThat(hologram.withActionMoved(0, 2).actions()).containsExactly(b, c, a);
        assertThat(hologram.withActionMoved(2, 0).actions()).containsExactly(c, a, b);
    }

    @Test
    void withActionMovedRejectsAnOutOfRangeIndex() {
        Hologram hologram = fresh().withActionAdded(action("a")).withActionAdded(action("b"));

        assertThatThrownBy(() -> hologram.withActionMoved(0, 2)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> hologram.withActionMoved(2, 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> hologram.withActionMoved(-1, 0)).isInstanceOf(IndexOutOfBoundsException.class);
    }

    @Test
    void withActionsClearedEmptiesTheList() {
        Hologram hologram = fresh().withActionAdded(action("a"))
                .withActionAdded(action("b"))
                .withActionsCleared();

        assertThat(hologram.actions()).isEmpty();
        assertThat(hologram.hasActions()).isFalse();
    }

    @Test
    void theReturnedActionListIsImmutable() {
        Hologram hologram = fresh().withActionAdded(action("a"));

        assertThatThrownBy(() -> hologram.actions().add(action("b"))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void everyOtherFieldIsPreservedAcrossAnActionEdit() {
        Hologram hologram = fresh().withGrowUp(true).withRefreshIntervalTicks(20);

        Hologram edited = hologram.withActionAdded(action("hi"));

        assertThat(edited.growUp()).isTrue();
        assertThat(edited.refreshIntervalTicks()).isEqualTo(20);
        assertThat(edited.name()).isEqualTo(hologram.name());
        assertThat(edited.createdAt()).isEqualTo(CREATED);
        assertThat(edited.lines()).map(HologramLine::value).containsExactly("x");
    }

    @Test
    void addingPastTheCapIsRejected() {
        Hologram hologram = fresh();
        for (int i = 0; i < Hologram.MAX_ACTIONS; i++) {
            hologram = hologram.withActionAdded(action("a" + i));
        }
        assertThat(hologram.actions()).hasSize(Hologram.MAX_ACTIONS);

        Hologram full = hologram;
        assertThatThrownBy(() -> full.withActionAdded(action("over"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> full.withActionInsertedAt(0, action("over")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
