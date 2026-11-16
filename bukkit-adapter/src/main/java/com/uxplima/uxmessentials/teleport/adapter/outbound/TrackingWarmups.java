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
        Optional<Position> origin = locator.locate(who);
        tracker.supersede(who.uuid());
        AtomicReference<WarmupHandle> started = new AtomicReference<>();
        WarmupHandle handle = delegate.begin(who, kind, wrapComplete(who, started, onComplete), onCancel, onSuperseded);
        started.set(handle);
        origin.ifPresent(at -> arm(who, kind, at, handle));
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

    private void arm(PlayerRef who, WarmupKind kind, Position origin, WarmupHandle handle) {
        if (handle.isComplete()) {
            return;
        }
        // A real destination is not needed for the move comparison. The origin block is what the
        // invariant pins. The destination/kind fields are carried only for diagnostics, so a neutral
        // marker keeps the tracker honest without re-plumbing the engine's destination through the port.
        PendingTeleport warmup =
                PendingTeleport.arm(who, TeleportKind.ADMIN, origin, Destination.at(origin), toggles.get());
        Duration waiting = resolveDuration(who, kind);
        tracker.arm(who, warmup, handle, clock.instant().plus(waiting));
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
