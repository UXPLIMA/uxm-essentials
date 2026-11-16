package com.uxplima.uxmessentials.worlds.application.port;

import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.application.teleport.TeleportOutcome;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.worlds.domain.WorldTeleportCause;

/**
 * Sends a player to a world-entry destination, delegating to the shared teleport context so the
 * world's entry teleport obeys the same warmup, cooldown, and move-cancels-warmup invariants as every
 * other teleport. The worlds context owns the destination and the {@link WorldTeleportCause}; the
 * mechanics belong to the teleport adapter behind this port.
 */
public interface WorldTeleporter {

    /**
     * On {@code who}'s region thread: begins a teleport of {@code who} to {@code to} for the given {@code cause}.
     *
     * @return whether the teleport was accepted (a warmup was queued). The implementation owns the
     *     warmup, cooldown, and the player-facing notification on its own failures, so a {@code false}
     *     here means the request was rejected outright (already notified), not that the teleport failed
     *     silently.
     */
    default boolean teleport(PlayerRef who, Position to, WorldTeleportCause cause) {
        return teleport(who, to, cause, ended -> {});
    }

    /**
     * {@link #teleport(PlayerRef, Position, WorldTeleportCause)}, reporting how the teleport ended to {@code outcome}
     * exactly once: {@link TeleportOutcome#LANDED} when the player arrives, and otherwise why they did not (a refused
     * launch, a warmup cut short, a hop that missed). A paid entry needs it to give the fee back when the player was
     * never moved.
     */
    boolean teleport(PlayerRef who, Position to, WorldTeleportCause cause, Consumer<TeleportOutcome> outcome);

    /**
     * On {@code who}'s region thread: finish a hop from another server by moving {@code who} to {@code to} at once,
     * with no warmup, since the origin already ran the teleport rules before the player left. Exactly one of
     * {@code onLanded} and {@code onMissed} runs: {@code onLanded} once the player stands there, {@code onMissed} when
     * the move is refused or fails, so the caller can give back what the hop cost.
     */
    void arrive(PlayerRef who, Position to, WorldTeleportCause cause, Runnable onLanded, Runnable onMissed);
}
