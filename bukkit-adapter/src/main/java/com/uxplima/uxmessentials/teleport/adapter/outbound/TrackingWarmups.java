package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.inbound.listener.WarmupTracker;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.PendingTeleport;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.WarmupCancelToggles;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link Warmups} decorator that registers every warmup the teleport engine begins with the
 * {@link WarmupTracker}, so the move/damage listeners can cancel it. The kernel's {@code SchedulerWarmups}
 * does the duration resolution and the countdown; this decorator only snapshots the player's origin block
 * and the configured cancel toggles at {@code begin} time and arms the tracker with the returned handle.
 *
 * <p>Wrapping the port — rather than threading the handle back through the engine — keeps the cooldown /
 * warmup orchestration in {@code :core} untouched while still giving the adapter the live handle the
 * move-cancels-warmup invariant needs. A zero-duration (bypass) warmup returns an already-complete handle
 * the tracker drops, so a bypassed teleport is correctly immune to move-cancel.
 */
@NullMarked
public final class TrackingWarmups implements Warmups {

    private final Warmups delegate;
    private final WarmupTracker tracker;
    private final Supplier<WarmupCancelToggles> toggles;

    public TrackingWarmups(Warmups delegate, WarmupTracker tracker, Supplier<WarmupCancelToggles> toggles) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
    }

    @Override
    public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
        Objects.requireNonNull(who, "who");
        Optional<Position> origin = currentPosition(who);
        WarmupHandle handle = delegate.begin(who, kind, wrapComplete(who, onComplete), onCancel);
        origin.ifPresent(at -> arm(who, at, handle));
        return handle;
    }

    private Runnable wrapComplete(PlayerRef who, Runnable onComplete) {
        return () -> {
            tracker.forget(who.uuid());
            onComplete.run();
        };
    }

    private void arm(PlayerRef who, Position origin, WarmupHandle handle) {
        if (handle.isComplete()) {
            return;
        }
        // A real destination is not needed for the move comparison — the origin block is what the
        // invariant pins. The destination/kind fields are carried only for diagnostics, so a neutral
        // marker keeps the tracker honest without re-plumbing the engine's destination through the port.
        PendingTeleport warmup =
                PendingTeleport.arm(who, TeleportKind.ADMIN, origin, Destination.at(origin), toggles.get());
        tracker.arm(who, warmup, handle);
    }

    private static Optional<Position> currentPosition(PlayerRef who) {
        Player player = Bukkit.getPlayer(who.uuid());
        return player == null || !player.isOnline() ? Optional.empty() : Optional.of(TeleportRefs.positionOf(player));
    }
}
