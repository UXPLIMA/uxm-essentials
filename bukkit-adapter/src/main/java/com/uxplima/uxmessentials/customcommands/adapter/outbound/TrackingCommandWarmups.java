package com.uxplima.uxmessentials.customcommands.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.customcommands.adapter.inbound.listener.CommandWarmupTracker;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
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
 * during the chain from cancelling a handle that has already fired.
 */
@NullMarked
public final class TrackingCommandWarmups implements Warmups {

    private final Warmups delegate;
    private final CommandWarmupTracker tracker;

    public TrackingCommandWarmups(Warmups delegate, CommandWarmupTracker tracker) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.tracker = Objects.requireNonNull(tracker, "tracker");
    }

    @Override
    public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(onComplete, "onComplete");
        Objects.requireNonNull(onCancel, "onCancel");
        Optional<Position> origin = positionOf(who);
        WarmupHandle handle = delegate.begin(
                who,
                kind,
                () -> {
                    tracker.forget(who.uuid());
                    onComplete.run();
                },
                () -> {
                    tracker.forget(who.uuid());
                    onCancel.run();
                });
        origin.ifPresent(at -> tracker.arm(who, at, handle));
        return handle;
    }

    /** Where the player stands right now, or empty when they are not online for us to anchor on. */
    private static Optional<Position> positionOf(PlayerRef who) {
        Player live = Bukkit.getPlayer(who.uuid());
        if (live == null) {
            return Optional.empty();
        }
        return Optional.of(BukkitRefs.toPosition(Objects.requireNonNull(live.getLocation(), "location")));
    }
}
