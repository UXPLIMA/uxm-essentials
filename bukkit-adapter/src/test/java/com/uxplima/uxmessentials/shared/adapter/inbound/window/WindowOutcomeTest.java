package com.uxplima.uxmessentials.shared.adapter.inbound.window;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * A window asked for is decided once, as shown or as refused. An open that was not deferred is decided as it returns;
 * a deferred one by its window turning up, or by the check after the deferral. A refused window is held shut.
 */
class WindowOutcomeTest {

    private final List<String> told = new ArrayList<>();
    private final WindowOutcome outcome = new WindowOutcome(() -> told.add("shown"), () -> told.add("refused"));

    @Test
    void aWindowShowingRightAfterTheOpenIsShownOnce() {
        outcome.asked(false, true);
        outcome.shown();
        outcome.settle(false);

        assertThat(told).containsExactly("shown");
        assertThat(outcome.mayOpen()).isTrue();
    }

    @Test
    void aCancelledOpenIsRefusedOnceAndAWindowTurningUpLaterIsHeldShut() {
        outcome.asked(false, false);
        outcome.refused();

        assertThat(outcome.mayOpen()).isFalse();
        outcome.shown();
        assertThat(told).containsExactly("refused");
    }

    @Test
    void aDeferredOpenIsUndecidedUntilItsWindowTurnsUp() {
        outcome.asked(true, false);
        assertThat(outcome.decided()).isFalse();
        assertThat(outcome.mayOpen()).isTrue();

        outcome.shown();
        outcome.settle(false);

        assertThat(told).containsExactly("shown");
    }

    @Test
    void aDeferredOpenThatEndedWithoutAWindowIsRefusedOnceAndALateWindowIsHeldShut() {
        outcome.asked(true, false);
        assertThat(told).isEmpty();

        outcome.settle(false);
        outcome.settle(false);
        outcome.refused();

        assertThat(told).containsExactly("refused");
        assertThat(outcome.mayOpen()).isFalse();
    }

    @Test
    void aDeferredOpenWhoseWindowIsShowingAtTheCheckIsShown() {
        outcome.asked(true, false);

        outcome.settle(true);

        assertThat(told).containsExactly("shown");
    }

    @Test
    void aRefusedWindowSeenShowingAnywayMustBeClosedAndStaysRefused() {
        outcome.asked(false, false);

        assertThat(outcome.shown()).isFalse();
        assertThat(outcome.settle(true)).isFalse();

        assertThat(told).containsExactly("refused");
    }

    @Test
    void aShownWindowMayStay() {
        assertThat(outcome.asked(false, true)).isTrue();
        assertThat(outcome.shown()).isTrue();

        assertThat(told).containsExactly("shown");
    }

    @Test
    void anOpenRuledOutBeforeItsWindowIsRefusedWhenTheWindowIsSeen() {
        boolean[] ruledOut = {false};
        WindowOutcome guarded =
                new WindowOutcome(() -> told.add("shown"), () -> told.add("refused"), () -> ruledOut[0]);
        guarded.asked(true, false);
        ruledOut[0] = true;

        assertThat(guarded.mayOpen()).isFalse();
        assertThat(guarded.shown()).isFalse();

        assertThat(told).containsExactly("refused");
    }
}
