package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.NullMarked;

/**
 * Decides, exactly once, how a confirmed trade ends after its two window freezes: settle and swap, or hand each side
 * its own offer back.
 *
 * <p>Once both sides confirm, the commit owns the trade, so a quit or a window close no longer cancels it. Each side's
 * window is then frozen on that player's own thread. A player who leaves in that tick never gets the hop, and before
 * this gate the settle waited forever for it while both offers sat in memory and were lost. Now a retired hop aborts
 * the commit: when the other hop is done too, nothing is settled and each side gets its own offer back. A plugin stop
 * takes the trade over directly, since the scheduler runs no more hops by then.
 */
@NullMarked
final class CommitGate {

    /** What the caller does now. */
    enum Decision {
        /** Nothing yet, or nothing at all: another path owns the ending. */
        WAIT,
        /** Both windows are frozen: settle the money and swap the items. */
        SETTLE,
        /** The commit was aborted: give each side its own offer back. */
        RETURN
    }

    private static final int FREEZING = 0;
    private static final int ABORTING = 1;
    private static final int SETTLING = 2;
    private static final int RETURNED = 3;

    private final AtomicInteger hopsLeft;
    private final AtomicInteger state = new AtomicInteger(FREEZING);

    CommitGate(int hops) {
        if (hops <= 0) {
            throw new IllegalArgumentException("hops must be positive: " + hops);
        }
        this.hopsLeft = new AtomicInteger(hops);
    }

    /** One side's window was frozen on its player's thread. */
    Decision frozen() {
        return hopDone();
    }

    /** One side's player left before their window could be frozen; the commit can no longer settle. */
    Decision retired() {
        state.compareAndSet(FREEZING, ABORTING);
        return hopDone();
    }

    /**
     * The plugin is stopping and no more hops will run. True when the caller now owns the ending and must give each
     * side its own offer back; false when the settle or a return already owns it.
     */
    boolean takeOverForStop() {
        return state.compareAndSet(FREEZING, RETURNED) || state.compareAndSet(ABORTING, RETURNED);
    }

    private Decision hopDone() {
        if (hopsLeft.decrementAndGet() != 0) {
            return Decision.WAIT;
        }
        if (state.compareAndSet(FREEZING, SETTLING)) {
            return Decision.SETTLE;
        }
        if (state.compareAndSet(ABORTING, RETURNED)) {
            return Decision.RETURN;
        }
        return Decision.WAIT;
    }
}
