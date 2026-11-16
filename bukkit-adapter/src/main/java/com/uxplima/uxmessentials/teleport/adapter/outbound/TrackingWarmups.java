package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.Warmups.CancelCause;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.PendingTeleport;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelToggles;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link Warmups} decorator that registers every warmup the teleport engine begins with the
 * {@link WarmupTracker}, so the move/damage listeners can cancel it. The kernel's {@code SchedulerWarmups}
 * does the duration resolution and the countdown; this decorator only snapshots the player's origin block
 * and the configured cancel toggles at {@code begin} time and arms the tracker with the returned handle.
 *
 * <p>Wrapping the port, rather than threading the handle back through the engine, keeps the cooldown /
 * warmup orchestration in {@code :core} untouched while still giving the adapter the live handle the
 * move-cancels-warmup invariant needs. A zero-duration (bypass) warmup returns an already-complete handle
 * the tracker drops, so a bypassed teleport is correctly immune to move-cancel.
 *
 * <p>The wrapper is also what tells the player to stand still. The engine wires the cancel callback and
 * announced nothing at the start, so a player who ran a command with a warmup waited three seconds with no
 * notice and {@code teleport.warmup.started} shipped in ten languages unsent. The notice belongs here and
 * not in the engine because this is where the real length is known: the engine reads the config default
 * and the tier node a player holds is resolved below.
 *
 * <p>The wrapper also resolves the warmup's duration here, the same min-reducer the kernel
 * {@code SchedulerWarmups} applies, and records the resulting completion instant with the tracker, so the
 * {@code teleport_warmup_remaining} placeholder can read the remaining wait the kernel port does not surface
 * back through its handle.
 *
 * <p>Before a new warmup begins, the player's earlier one, if any is still armed, is superseded. Doing it here
 * rather than only when the tracker is armed also covers a new teleport that skips its warmup: it lands at once and
 * is never armed, and the earlier warmup must not land after it.
 *
 * <p>A warmup begun for a player who is not online, who quits while it is being set up (a command run off the
 * player's thread can race the quit), or whose quit is still being handled, is cancelled as a quit here: the quit
 * listener cannot reach a warmup that was not armed yet, and the countdown would otherwise land the player after a
 * fast relog. Such a setup neither replaces nor arms anything, so the session after the relog keeps its own warmup.
 */
@NullMarked
public final class TrackingWarmups implements Warmups {

    private final Warmups delegate;
    private final WarmupTracker tracker;
    private final Supplier<WarmupCancelToggles> toggles;
    private final Permissions permissions;
    private final Clock clock;
    private final Notifier notifier;
    private final PlayerLocator locator;
    private final Logger log;

    public TrackingWarmups(
            Warmups delegate,
            WarmupTracker tracker,
            Supplier<WarmupCancelToggles> toggles,
            Permissions permissions,
            Clock clock,
            Notifier notifier,
            PlayerLocator locator,
            Logger log) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.locator = Objects.requireNonNull(locator, "locator");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
        return begin(who, kind, onComplete, onCancel, onCancel);
    }

    @Override
    public WarmupHandle begin(
            PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel, Runnable onSuperseded) {
        Objects.requireNonNull(onCancel, "onCancel");
        return begin(who, kind, onComplete, (CancelCause cause) -> onCancel.run(), onSuperseded);
    }

    @Override
    public WarmupHandle begin(
            PlayerRef who,
            WarmupKind kind,
            Runnable onComplete,
            Consumer<CancelCause> onCancel,
            Runnable onSuperseded) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        Object ticket = tracker.beginning(who.uuid());
        WarmupHandle handle;
        try {
            handle = beginArmed(who, ticket, kind, onComplete, onCancel, onSuperseded);
        } catch (RuntimeException failure) {
            var ignored = tracker.stillHere(who.uuid(), ticket);
            throw failure;
        }
        if (!tracker.stillHere(who.uuid(), ticket)) {
            // The player quit while this warmup was being set up, so the quit listener may have run before it was
            // armed. Only this warmup is stopped: one a relogged player starts meanwhile belongs to the new session.
            tracker.forget(who.uuid(), handle);
            handle.cancel(CancelCause.QUIT);
        }
        return handle;
    }

    private WarmupHandle beginArmed(
            PlayerRef who,
            Object ticket,
            WarmupKind kind,
            Runnable onComplete,
            Consumer<CancelCause> onCancel,
            Runnable onSuperseded) {
        Optional<Position> origin = locator.locate(who);
        // Bound to the ticket, so a setup whose session ended leaves the next session's warmup running.
        tracker.supersede(who.uuid(), ticket);
        AtomicReference<WarmupHandle> started = new AtomicReference<>();
        WarmupHandle handle = delegate.begin(who, kind, wrapComplete(who, started, onComplete), onCancel, onSuperseded);
        started.set(handle);
        if (origin.isEmpty()) {
            // Nobody to arm against: the player is already gone. Left running, the countdown would find them by uuid
            // after a relog and land them in a session that never asked for it.
            handle.cancel(CancelCause.QUIT);
            return handle;
        }
        arm(who, ticket, kind, origin.get(), handle);
        return handle;
    }

    private Runnable wrapComplete(PlayerRef who, AtomicReference<WarmupHandle> started, Runnable onComplete) {
        return () -> {
            // Empty only for a warmup that completed inside begin, which was never armed and has nothing to forget.
            WarmupHandle self = started.get();
            if (self != null) {
                tracker.forget(who.uuid(), self);
            }
            onComplete.run();
        };
    }

    private void arm(PlayerRef who, Object ticket, WarmupKind kind, Position origin, WarmupHandle handle) {
        if (handle.isComplete()) {
            return;
        }
        // A real destination is not needed for the move comparison. The origin block is what the
        // invariant pins. The destination/kind fields are carried only for diagnostics, so a neutral
        // marker keeps the tracker honest without re-plumbing the engine's destination through the port.
        PendingTeleport warmup =
                PendingTeleport.arm(who, TeleportKind.ADMIN, origin, Destination.at(origin), toggles.get());
        Duration waiting = resolveDuration(who, kind);
        if (!tracker.arm(who, ticket, warmup, handle, clock.instant().plus(waiting))) {
            return; // the player's session ended during the setup, which cancels this warmup right after
        }
        // The warmup is already counting down here, so a notice that cannot be sent must not throw out of begin:
        // the caller would take the teleport for one that never started while it still lands.
        try {
            notifier.send(
                    who, TeleportMessageKey.WARMUP_STARTED, Map.of("seconds", Long.toString(waiting.toSeconds())));
        } catch (RuntimeException noticeFailure) {
            log.warn("warmup notice for {} could not be sent: {}", who.name(), noticeFailure.toString());
        }
    }

    private Duration resolveDuration(PlayerRef who, WarmupKind kind) {
        if (permissions.has(who, kind.bypassNode())) {
            return Duration.ZERO;
        }
        QuotaFamily family = QuotaFamily.threshold(kind.warmupNode());
        long seconds = permissions
                .resolveQuota(who, family, null, kind.defaultSeconds())
                .orElse(kind.defaultSeconds());
        return Duration.ofSeconds(Math.max(0L, seconds));
    }
}
