package com.uxplima.uxmessentials.shared.adapter.inbound.window;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.NullMarked;

/**
 * Whether one window asked for came up, decided once: shown, or refused, and never changed after. Whatever hangs on
 * the answer (a fee kept or given back, a confirmation, a caller's future) runs exactly once, from whichever signal
 * comes first.
 *
 * <p>An open that was not deferred is decided as soon as the open returns, by whether the window is showing then. A
 * deferred one (uxmLib puts a sleeping player's open off by a tick, and drops it when they are still asleep) is decided
 * by its open event, by a check once the deferral has had its chance, or by the viewer leaving. Once refused,
 * the window is held shut: a late open must not show a window whose outcome already went the other way.
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

    /**
     * @param onShown runs once if the window is decided shown
     * @param onRefused runs once if the window is decided refused
     */
    public WindowOutcome(Runnable onShown, Runnable onRefused) {
        this.onShown = Objects.requireNonNull(onShown, "onShown");
        this.onRefused = Objects.requireNonNull(onRefused, "onRefused");
    }

    /**
     * The open was asked for and has returned. Not deferred, it has been decided by now: {@code showing} says how.
     * Deferred, nothing is known yet.
     */
    public void asked(boolean deferred, boolean showing) {
        if (!deferred) {
            settle(showing);
        }
    }

    /** The window's open event fired. False when it must be cancelled, because the outcome is already refused. */
    public boolean mayOpen() {
        return state.get() != State.REFUSED;
    }

    /**
     * The window was seen in front of the player: after its open event, or as it closed, which only a window that was
     * showing does.
     */
    public void shown() {
        if (state.compareAndSet(State.PENDING, State.SHOWN)) {
            onShown.run();
        }
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
     * the viewer is leaving, or the window is being shut down.
     */
    public void settle(boolean showing) {
        if (showing) {
            shown();
        } else {
            refused();
        }
    }
}
