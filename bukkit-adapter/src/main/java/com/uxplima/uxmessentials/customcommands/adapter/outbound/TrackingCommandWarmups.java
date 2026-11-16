package com.uxplima.uxmessentials.customcommands.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.customcommands.adapter.inbound.listener.CommandWarmupTracker;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The kernel {@link Warmups} port with this context's move-cancel wrapped around it. Every warmup a custom command
 * starts still counts down in the shared implementation; this decorator only remembers where the player stood and
 * hands the live handle to {@link CommandWarmupTracker}, which cancels it when they walk off that block.
 *
 * <p>A warmup that is already complete when {@code begin} returns (a zero-second declaration, or a player holding
 * the bypass node) is never armed, so the immediate case costs nothing and cannot be cancelled after the fact. The
 * completion callback is wrapped so a finished warmup is forgotten before the chain runs, which stops a move made
 * during the chain from cancelling a handle that has already fired. Completion and cancel forget only their own
 * handle, so an earlier warmup ending late never drops the entry of a newer one.
 *
 * <p>Before a new warmup begins, the player's earlier one, if any is still armed, is superseded, the same rule the
 * teleport context follows. Doing it here rather than only when the tracker is armed also covers a new run that
 * skips its warmup: it runs at once and is never armed, and the earlier warmup must not run and charge after it.
 */
@NullMarked
public final class TrackingCommandWarmups implements Warmups {

    private final Warmups delegate;
    private final CommandWarmupTracker tracker;
    private final PlayerLocator locator;

    public TrackingCommandWarmups(Warmups delegate, CommandWarmupTracker tracker, PlayerLocator locator) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.locator = Objects.requireNonNull(locator, "locator");
    }

    @Override
    public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
        return begin(who, kind, onComplete, onCancel, onCancel);
    }

    @Override
    public WarmupHandle begin(
            PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel, Runnable onSuperseded) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(onComplete, "onComplete");
        Objects.requireNonNull(onCancel, "onCancel");
        Objects.requireNonNull(onSuperseded, "onSuperseded");
        Optional<Position> origin = locator.locate(who);
        tracker.supersede(who.uuid());
        AtomicReference<WarmupHandle> started = new AtomicReference<>();
        WarmupHandle handle = delegate.begin(
                who,
                kind,
                forgetting(who, started, onComplete),
                forgetting(who, started, onCancel),
                forgetting(who, started, onSuperseded));
        started.set(handle);
        origin.ifPresent(at -> tracker.arm(who, at, handle));
        return handle;
    }

    /** Wrap {@code then} so the warmup that ends drops only its own tracker entry before {@code then} runs. */
    private Runnable forgetting(PlayerRef who, AtomicReference<WarmupHandle> started, Runnable then) {
        return () -> {
            // Empty only for a warmup that ended inside begin, which was never armed and has nothing to forget.
            WarmupHandle self = started.get();
            if (self != null) {
                tracker.forget(who.uuid(), self);
            }
            then.run();
        };
    }
}
