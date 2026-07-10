package com.uxplima.uxmessentials.playerwarps.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class RentStateTest {

    private static final Instant PAID_UNTIL = Instant.parse("2026-07-10T12:00:00Z");

    private static RentState paidUpThrough(Instant paidUntil) {
        return new RentState(paidUntil, Optional.empty(), Optional.empty());
    }

    @Test
    void keepsAllThreeFields() {
        Instant suspended = PAID_UNTIL.plusSeconds(60);
        Instant archive = PAID_UNTIL.plusSeconds(120);
        RentState state = new RentState(PAID_UNTIL, Optional.of(suspended), Optional.of(archive));
        assertThat(state.paidUntil()).isEqualTo(PAID_UNTIL);
        assertThat(state.suspendedAt()).contains(suspended);
        assertThat(state.archiveAfter()).contains(archive);
    }

    @Test
    @SuppressWarnings("NullAway") // verifies the compact constructor rejects a literal null in each field
    void rejectsNullFields() {
        assertThatNullPointerException().isThrownBy(() -> new RentState(null, Optional.empty(), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new RentState(PAID_UNTIL, null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new RentState(PAID_UNTIL, Optional.empty(), null));
    }

    @Test
    void isPaidThroughBeforeTheDeadline() {
        assertThat(paidUpThrough(PAID_UNTIL).isPaidThrough(PAID_UNTIL.minusSeconds(1)))
                .isTrue();
    }

    @Test
    void isStillPaidExactlyAtTheDeadline() {
        assertThat(paidUpThrough(PAID_UNTIL).isPaidThrough(PAID_UNTIL)).isTrue();
    }

    @Test
    void isNoLongerPaidAfterTheDeadline() {
        assertThat(paidUpThrough(PAID_UNTIL).isPaidThrough(PAID_UNTIL.plusSeconds(1)))
                .isFalse();
    }

    @Test
    @SuppressWarnings("NullAway") // verifies isPaidThrough rejects a literal null argument
    void isPaidThroughRejectsNull() {
        RentState state = paidUpThrough(PAID_UNTIL);
        assertThatNullPointerException().isThrownBy(() -> state.isPaidThrough(null));
    }

    @Test
    void isSuspendedReflectsWhetherASuspensionInstantIsPresent() {
        assertThat(paidUpThrough(PAID_UNTIL).isSuspended()).isFalse();
        RentState suspended = new RentState(PAID_UNTIL, Optional.of(PAID_UNTIL.plusSeconds(1)), Optional.empty());
        assertThat(suspended.isSuspended()).isTrue();
    }
}
