package com.uxplima.uxmessentials.teleport.domain.event;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;

/**
 * A move-cancellable warmup began for a player; the move-cancel comparison anchors on {@link #origin}.
 *
 * @param player the player counting down
 * @param kind the teleport this warmup precedes
 * @param origin the block the player must not leave for the warmup to complete
 * @param duration the resolved warmup length
 */
public record WarmupStarted(PlayerRef player, TeleportKind kind, Position origin, Duration duration)
        implements TeleportEvent {

    public WarmupStarted {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(duration, "duration");
    }
}
