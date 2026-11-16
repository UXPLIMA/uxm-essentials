package com.uxplima.uxmessentials.customcommands.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/** One armed command warmup per player: what a second arm, a move and a late ending each do to the handles. */
class CommandWarmupTrackerTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e5"), "Alex");
    private static final PlayerRef SAM =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000005a77"), "Sam");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-0000000000f1"), "world");
    private static final Position STANDING = new Position(WORLD, 10.5, 64, 10.5, 0f, 0f);
    private static final Position TURNED = new Position(WORLD, 10.9, 64, 10.2, 90f, 10f);
    private static final Position WALKED_OFF = new Position(WORLD, 14.5, 64, 10.5, 0f, 0f);

    private final CommandWarmupTracker tracker = new CommandWarmupTracker();

    @Test
    void armingASecondWarmupSupersedesTheFirstOnceAndAMoveThenStopsOnlyTheSecond() {
        Counted first = new Counted();
        Counted second = new Counted();

        tracker.arm(ALEX, STANDING, first);
        tracker.arm(ALEX, STANDING, second);
        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        assertThat(first.superseded).isEqualTo(1);
        assertThat(first.cancelled).isZero();
        assertThat(second.superseded).isZero();
        assertThat(second.cancelled).isEqualTo(1);
    }

    @Test
    void aFirstWarmupThatEndsAfterBeingReplacedLeavesTheNewOneTracked() {
        Counted first = new Counted();
        Counted second = new Counted();
        tracker.arm(ALEX, STANDING, first);
        tracker.arm(ALEX, STANDING, second);

        tracker.forget(ALEX.uuid(), first);
        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        assertThat(second.cancelled)
                .as("the late ending of the replaced warmup did not drop the newer one")
                .isEqualTo(1);
    }

    @Test
    void aWarmupForgottenByItsOwnHandleIsNoLongerCancelledByAMove() {
        Counted done = new Counted();
        tracker.arm(ALEX, STANDING, done);

        tracker.forget(ALEX.uuid(), done);
        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        assertThat(done.cancelled).isZero();
        assertThat(tracker.tracked()).isZero();
    }

    @Test
    void supersedingStopsTheArmedWarmupOnceAndLeavesNothingTracked() {
        Counted waiting = new Counted();
        tracker.arm(ALEX, STANDING, waiting);

        tracker.supersede(ALEX.uuid());
        tracker.supersede(ALEX.uuid());
        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        assertThat(waiting.superseded).isEqualTo(1);
        assertThat(waiting.cancelled).isZero();
        assertThat(tracker.tracked()).isZero();
    }

    @Test
    void quittingCancelsTheArmedWarmupOnce() {
        Counted waiting = new Counted();
        tracker.arm(ALEX, STANDING, waiting);

        tracker.quit(ALEX.uuid());
        tracker.quit(ALEX.uuid());

        assertThat(waiting.cancelled)
                .as("a fast relog finds nothing left to run or charge")
                .isEqualTo(1);
        assertThat(tracker.tracked()).isZero();
    }

    @Test
    void aHandleThatAlreadyEndedIsNotArmed() {
        Counted cancelled = new Counted();
        cancelled.cancel();

        tracker.arm(ALEX, STANDING, cancelled);

        assertThat(tracker.tracked()).isZero();
    }

    @Test
    void aCancelWhoseCallbackForgetsItsOwnEntryLeavesTheCountRight() {
        Counted forgetsItself = new Counted();
        forgetsItself.onStop = () -> tracker.forget(ALEX.uuid(), forgetsItself);
        tracker.arm(ALEX, STANDING, forgetsItself);

        tracker.onMove(ALEX.uuid(), WALKED_OFF);
        tracker.arm(SAM, STANDING, new Counted());

        assertThat(forgetsItself.cancelled).isEqualTo(1);
        assertThat(tracker.tracked())
                .as("the entry left the map once, not once per caller, so the next player counts")
                .isEqualTo(1);
    }

    @Test
    void turningOnTheSameBlockKeepsTheWarmup() {
        Counted waiting = new Counted();
        tracker.arm(ALEX, STANDING, waiting);

        tracker.onMove(ALEX.uuid(), TURNED);

        assertThat(waiting.cancelled).isZero();
        assertThat(tracker.tracked()).isEqualTo(1);
    }

    /** A handle that counts each way it was stopped and runs {@code onStop} after, as a real callback would. */
    private static final class Counted implements WarmupHandle {

        private int cancelled;
        private int superseded;
        private Runnable onStop = () -> {};

        @Override
        public void cancel() {
            cancelled++;
            onStop.run();
        }

        @Override
        public void supersede() {
            superseded++;
            onStop.run();
        }

        @Override
        public boolean isComplete() {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return cancelled + superseded > 0;
        }
    }
}
