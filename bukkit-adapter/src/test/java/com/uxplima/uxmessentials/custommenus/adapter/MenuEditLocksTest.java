package com.uxplima.uxmessentials.custommenus.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Pure-JUnit coverage of the {@link MenuEditLocks} registry: the first viewer takes a menu's lock, the same viewer
 * re-takes it (reentrant, so their own navigation never locks them out), a second viewer is refused with the holder's
 * name, and a release (return-to-browser or quit) frees it. Acquiring a second menu drops the first, so a viewer never
 * pins two.
 */
class MenuEditLocksTest {

    private final MenuEditLocks locks = new MenuEditLocks();
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void theFirstViewerAcquiresAFreeMenu() {
        assertThat(locks.tryAcquire("shop", alice, "Alice")).isEmpty();
        assertThat(locks.holds("shop", alice)).isTrue();
        assertThat(locks.heldBy("shop")).contains("Alice");
    }

    @Test
    void aSecondViewerIsRefusedWithTheHoldersName() {
        locks.tryAcquire("shop", alice, "Alice");

        assertThat(locks.tryAcquire("shop", bob, "Bob")).contains("Alice");
        assertThat(locks.holds("shop", bob)).isFalse();
        assertThat(locks.holds("shop", alice)).isTrue();
    }

    @Test
    void theSameViewerReacquiringIsReentrant() {
        locks.tryAcquire("shop", alice, "Alice");

        assertThat(locks.tryAcquire("shop", alice, "Alice")).isEmpty();
        assertThat(locks.holds("shop", alice)).isTrue();
    }

    @Test
    void releasingFreesTheMenuForAnotherViewer() {
        locks.tryAcquire("shop", alice, "Alice");

        locks.release(alice);

        assertThat(locks.heldBy("shop")).isEmpty();
        assertThat(locks.tryAcquire("shop", bob, "Bob")).isEmpty();
        assertThat(locks.holds("shop", bob)).isTrue();
    }

    @Test
    void acquiringASecondMenuReleasesTheFirst() {
        locks.tryAcquire("shop", alice, "Alice");

        assertThat(locks.tryAcquire("spawn", alice, "Alice")).isEmpty();

        assertThat(locks.heldBy("shop")).isEmpty();
        assertThat(locks.holds("spawn", alice)).isTrue();
        // The menu Alice left is now free for Bob.
        assertThat(locks.tryAcquire("shop", bob, "Bob")).isEmpty();
    }

    @Test
    void releasingAViewerWhoHoldsNothingIsANoOp() {
        locks.release(alice); // never acquired
        assertThat(locks.heldBy("shop")).isEmpty();
    }

    @Test
    void aReleaseNeverStealsAnotherViewersLock() {
        locks.tryAcquire("shop", alice, "Alice");
        // Bob holds nothing; releasing Bob must not drop Alice's lock on shop.
        locks.release(bob);
        assertThat(locks.holds("shop", alice)).isTrue();
    }

    @Test
    void heldByIsEmptyForAFreeMenu() {
        assertThat(locks.heldBy("nobody-here")).isEqualTo(Optional.empty());
    }
}
