package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupHandle;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.PendingTeleport;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelToggles;
import org.junit.jupiter.api.Test;

/** One armed warmup per player: what a second arm, a move and a late completion each do to the tracked handles. */
class WarmupTrackerTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e5"), "Alex");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-0000000000f1"), "world");
    private static final Position STANDING = new Position(WORLD, 10.5, 64, 10.5, 0f, 0f);
    private static final Position WALKED_OFF = new Position(WORLD, 14.5, 64, 10.5, 0f, 0f);
    private static final Instant LATER = Instant.parse("2026-11-15T12:00:03Z");

    private final WarmupTracker tracker = new WarmupTracker();

    private void arm(WarmupHandle handle) {
        PendingTeleport warmup = PendingTeleport.arm(
                ALEX,
                TeleportKind.HOME,
                STANDING,
                Destination.at(STANDING),
                new WarmupCancelToggles(true, false, false, false, 0.0));
        tracker.arm(ALEX, tracker.beginning(ALEX.uuid()), warmup, handle, LATER);
    }

    @Test
    void armingASecondWarmupSupersedesTheFirstOnceAndAMoveThenStopsOnlyTheSecond() {
        Counted first = new Counted();
        Counted second = new Counted();

        arm(first);
        arm(second);
        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        assertThat(first.superseded).isEqualTo(1);
        assertThat(first.cancelled).isZero();
        assertThat(second.superseded).isZero();
        assertThat(second.cancelled).isEqualTo(1);
    }

    @Test
    void aFirstWarmupThatCompletesAfterBeingReplacedLeavesTheNewOneTracked() {
        Counted first = new Counted();
        Counted second = new Counted();
        arm(first);
        arm(second);

        tracker.forget(ALEX.uuid(), first);
        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        assertThat(second.cancelled)
                .as("the late completion of the replaced warmup did not drop the newer one")
                .isEqualTo(1);
    }

    @Test
    void supersedingWithNothingArmedOrAfterAMoveTouchesNothing() {
        Counted moved = new Counted();
        arm(moved);
        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        tracker.supersede(ALEX.uuid());

        assertThat(moved.cancelled).isEqualTo(1);
        assertThat(moved.superseded).isZero();
        assertThat(tracker.completesAt(ALEX.uuid())).isEmpty();
    }

    @Test
    void aCancelWhoseCallbackStartsAnotherTeleportLeavesTheNewWarmupTracked() {
        Counted second = new Counted();
        // What a listener that starts a teleport on the cancel does: the new warmup supersedes, then arms, both on
        // the same player the cancel is for.
        Counted first = new Counted(() -> {
            tracker.supersede(ALEX.uuid());
            arm(second);
        });
        arm(first);

        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        assertThat(first.cancelled).isEqualTo(1);
        assertThat(tracker.isTracking(ALEX.uuid()))
                .as("the warmup armed from the cancel callback is still tracked")
                .isTrue();
        assertThat(tracker.completesAt(ALEX.uuid())).contains(LATER);

        tracker.onMove(ALEX.uuid(), WALKED_OFF);

        assertThat(second.cancelled).isEqualTo(1);
        assertThat(tracker.isTracking(ALEX.uuid())).isFalse();
    }

    /** A handle that counts how it was stopped, running {@code onCancel} when it is cancelled. */
    private static final class Counted implements WarmupHandle {

        private final Runnable onCancel;
        private int cancelled;
        private int superseded;

        Counted() {
            this(() -> {});
        }

        Counted(Runnable onCancel) {
            this.onCancel = onCancel;
        }

        @Override
        public void cancel() {
            cancelled++;
            onCancel.run();
        }

        @Override
        public void supersede() {
            superseded++;
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
