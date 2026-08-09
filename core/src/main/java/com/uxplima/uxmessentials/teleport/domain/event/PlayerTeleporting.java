package com.uxplima.uxmessentials.teleport.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;

/**
 * A player is about to be teleported by uxmEssentials.
 *
 * <p>Asked once the plugin's own gates have passed (not jailed, not in combat, off cooldown, able to pay) and before
 * the warmup starts, so a refusal costs the player nothing and does not leave them standing still for a teleport that
 * was never going to happen.
 *
 * <p>Not asked for an involuntary arrival: a respawn or a first-join drop has to put the player somewhere, and a
 * refusal there would leave them nowhere.
 *
 * @param player who would move
 * @param kind what kind of teleport it is
 * @param to where they would land
 */
public record PlayerTeleporting(PlayerRef player, TeleportKind kind, Position to) implements TeleportProposal {

    public PlayerTeleporting {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(to, "to");
    }
}
