package com.uxplima.uxmessentials.playerstate.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's game mode was set by {@code /gamemode} (or a {@code /gmc /gms /gma /gmsp} alias).
 *
 * @param subject the player whose mode changed
 * @param actor the player who ran the command
 * @param mode the new game mode
 * @param at when the change happened
 */
public record GameModeChanged(PlayerRef subject, PlayerRef actor, GameModeRef mode, Instant at)
        implements PlayerStateEvent {

    public GameModeChanged {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(at, "at");
    }
}
