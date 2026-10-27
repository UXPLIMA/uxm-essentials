package com.uxplima.uxmessentials.homes.domain;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleted;
import org.junit.jupiter.api.Test;

class HomeSetTest {

    private static final Instant LATER = NOW.plusSeconds(60);

    private static HomeSet withHomesIn(int... slots) {
        HomeSet set = HomeSet.empty(ALEX);
        for (int slot : slots) {
            set = set.createAt(HomeSlot.of(slot), at(slot, 64, 0), HomeLimit.noLimit(), 100, NOW)
                    .orElseThrow()
                    .result();
        }
        return set;
    }

    @Test
    void aHomeIsCreatedInAFreeSlotInsideTheLimit() {
        HomeSet.Change change = HomeSet.empty(ALEX)
                .createAt(HomeSlot.of(0), at(1, 64, 2), HomeLimit.of(3), 3, NOW)
                .orElseThrow();

        assertThat(change.result().count()).isEqualTo(1);
        assertThat(change.home().location()).isEqualTo(at(1, 64, 2));
        assertThat(change.home().isPublic()).isFalse();
        assertThat(change.event()).containsInstanceOf(HomeCreated.class);
    }

    @Test
    void aTakenSlotIsRefused() {
        assertThat(withHomesIn(0)
                        .createAt(HomeSlot.of(0), at(5, 64, 5), HomeLimit.of(5), 5, NOW)
                        .asError())
                .contains(HomeError.SLOT_TAKEN);
    }

    @Test
    void aSlotPastTheGridIsRefused() {
        assertThat(HomeSet.empty(ALEX)
                        .createAt(HomeSlot.of(3), at(0, 64, 0), HomeLimit.of(3), 3, NOW)
                        .asError())
                .contains(HomeError.SLOT_OUT_OF_RANGE);
    }

    @Test
    void theLimitStopsANewHomeEvenWhenASlotIsFree() {
        HomeSet two = withHomesIn(0, 1);

        assertThat(two.createAt(HomeSlot.of(4), at(0, 64, 0), HomeLimit.of(2), 10, NOW)
                        .asError())
                .contains(HomeError.LIMIT_REACHED);
        assertThat(two.limitReached(HomeLimit.of(2)).currentCount()).isEqualTo(2);
    }

    @Test
    void anUnlimitedQuotaUsesTheConfiguredSlotCeiling() {
        HomeLimit unlimited = HomeLimit.noLimit();

        assertThat(unlimited.isReachedAt(10_000)).isFalse();
        assertThat(unlimited.maxSlots(54)).isEqualTo(54);
        assertThat(HomeLimit.of(3).maxSlots(54)).isEqualTo(3);
    }

    @Test
    void aNegativeLimitDoesNotExist() {
        assertThatThrownBy(() -> HomeLimit.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theFirstFreeSlotIsTheLowestGap() {
        HomeSet set = withHomesIn(0, 1, 3);

        assertThat(set.firstFreeSlot(5)).contains(HomeSlot.of(2));
        assertThat(withHomesIn(0, 1).firstFreeSlot(2)).isEmpty();
    }

    @Test
    void homesAreListedBySlot() {
        assertThat(withHomesIn(4, 0, 2).all())
                .extracting(Home::slot)
                .containsExactly(HomeSlot.of(0), HomeSlot.of(2), HomeSlot.of(4));
    }

    @Test
    void relocatingKeepsTheLabelAndCreationTime() {
        HomeSet labelled = withHomesIn(0)
                .renameLabel(HomeSlot.of(0), Optional.of(HomeLabel.of("Base")), NOW)
                .orElseThrow()
                .result();

        Home moved = labelled.relocate(HomeSlot.of(0), at(9, 70, 9), LATER)
                .orElseThrow()
                .home();

        assertThat(moved.location()).isEqualTo(at(9, 70, 9));
        assertThat(moved.label()).contains(HomeLabel.of("Base"));
        assertThat(moved.createdAt()).isEqualTo(NOW);
        assertThat(moved.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void changingAnEmptySlotIsNotFound() {
        HomeSet empty = HomeSet.empty(ALEX);
        HomeSlot slot = HomeSlot.of(0);

        assertThat(List.of(
                        empty.relocate(slot, at(0, 64, 0), NOW).asError(),
                        empty.renameLabel(slot, Optional.empty(), NOW).asError(),
                        empty.setIcon(slot, Optional.empty(), NOW).asError(),
                        empty.setVisibility(slot, true, NOW).asError(),
                        empty.delete(slot).asError()))
                .containsOnly(Optional.of(HomeError.NOT_FOUND));
    }

    @Test
    void deletingFreesTheSlotAndLeavesTheOriginalSetUntouched() {
        HomeSet before = withHomesIn(0, 1);

        HomeSet.Change change = before.delete(HomeSlot.of(0)).orElseThrow();

        assertThat(change.result().find(HomeSlot.of(0))).isEmpty();
        assertThat(change.result().count()).isEqualTo(1);
        assertThat(change.event()).containsInstanceOf(HomeDeleted.class);
        assertThat(before.count()).isEqualTo(2);
    }

    @Test
    void sharingAHomeMakesItPublic() {
        Home shared = withHomesIn(0)
                .setVisibility(HomeSlot.of(0), true, NOW)
                .orElseThrow()
                .home();

        assertThat(shared.isPublic()).isTrue();
    }

    @Test
    void aLabelIsTrimmedAndKeepsItsCase() {
        assertThat(HomeLabel.of("  My Base ").value()).isEqualTo("My Base");
        assertThat(HomeLabel.of("Base")).isNotEqualTo(HomeLabel.of("base"));
    }

    @Test
    void aBlankOrOverlongLabelIsRefused() {
        assertThatThrownBy(() -> HomeLabel.of("   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HomeLabel.of("x".repeat(HomeLabel.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(HomeLabel.of("x".repeat(HomeLabel.MAX_LENGTH)).value()).hasSize(HomeLabel.MAX_LENGTH);
    }

    @Test
    void theSlotPlayersSeeCountsFromOne() {
        assertThat(HomeSlot.of(0).displayNumber()).isEqualTo(1);
        assertThatThrownBy(() -> HomeSlot.of(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
