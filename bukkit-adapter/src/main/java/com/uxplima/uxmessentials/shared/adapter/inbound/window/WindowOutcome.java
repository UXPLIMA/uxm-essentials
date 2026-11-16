package com.uxplima.uxmessentials.shared.adapter.inbound.window;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.NullMarked;

/**
 * Whether one window asked for came up, decided once: shown, or refused, and never changed after. Whatever hangs on
 * the answer (a fee kept or given back, a confirmation, a caller's future) runs exactly once, from whichever signal
 * comes first.
 *
 * <p>An open that was not deferred is decided as soon as the open returns, by whether the window is showing then. A
 * deferred one (uxmLib puts a sleeping player's open off by a tick, and drops it when they are still asleep) is decided
 * by its open event, by a check once the deferral has had its chance, or by the viewer leaving. Once refused, the
 * window is held shut: its open is cancelled, and a window seen showing anyway (a plugin that undid the cancel) must be
 * closed, since what hung on the outcome already went the other way.
 *
 * <p>Something outside the window can rule the open out before any signal comes (a fee already given back), in which
 * case the window counts as refused whatever is seen.
 */
@NullMarked
public final class WindowOutcome {

    private enum State {
        PENDING,
        SHOWN,
        REFUSED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.PENDING);
    private final Runnable onShown;
    private final Runnable onRefused;
    private final BooleanSupplier ruledOut;

    /**
     * @param onShown runs once if the window is decided shown
     * @param onRefused runs once if the window is decided refused
     */
    public WindowOutcome(Runnable onShown, Runnable onRefused) {
        this(onShown, onRefused, () -> false);
    }

    /**
     * @param onShown runs once if the window is decided shown
     * @param onRefused runs once if the window is decided refused
     * @param ruledOut true once the open may no longer be shown, whatever is seen
     */
    public WindowOutcome(Runnable onShown, Runnable onRefused, BooleanSupplier ruledOut) {
        this.onShown = Objects.requireNonNull(onShown, "onShown");
        this.onRefused = Objects.requireNonNull(onRefused, "onRefused");
        this.ruledOut = Objects.requireNonNull(ruledOut, "ruledOut");
    }

    /**
     * The open was asked for and has returned. Not deferred, it has been decided by now: {@code showing} says how.
     * Deferred, nothing is known yet. False when the window is showing but must be closed.
     */
    public boolean asked(boolean deferred, boolean showing) {
        return deferred || settle(showing);
    }

    /** The window's open event fired. False when it must be cancelled, because the open is refused or ruled out. */
    public boolean mayOpen() {
        return state.get() != State.REFUSED && !ruledOut.getAsBoolean();
    }

    /**
     * The window was seen in front of the player: after its open event, or as it closed, which only a window that was
     * showing does. Returns false when it may not stay: the open was refused or ruled out, and the window must be
     * closed.
     */
    public boolean shown() {
        if (ruledOut.getAsBoolean()) {
            refused();
            return false;
        }
        if (state.compareAndSet(State.PENDING, State.SHOWN)) {
            onShown.run();
        }
        return state.get() == State.SHOWN;
    }

    /** The open was refused or will never happen. */
    public void refused() {
        if (state.compareAndSet(State.PENDING, State.REFUSED)) {
            onRefused.run();
        }
    }

    /** True once the outcome is decided either way. */
    public boolean decided() {
        return state.get() != State.PENDING;
    }

    /**
     * Decide by whether the window is showing now, when no other signal will come: a deferred open has had its chance,
     * the viewer is leaving, or the window is being shut down. False when the window is showing but must be closed.
     */
    public boolean settle(boolean showing) {
        if (showing) {
            return shown();
        }
        refused();
        return true;
    }
}
