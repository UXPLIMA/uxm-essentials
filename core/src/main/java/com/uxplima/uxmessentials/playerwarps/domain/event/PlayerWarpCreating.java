package com.uxplima.uxmessentials.playerwarps.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * A player warp is about to be created.
 *
 * <p>Asked once the owner is known to be inside their quota, and before anything is written.
 *
 * @param owner whose warp it would be
 * @param name what it would be called
 * @param location where it would point
 */
public record PlayerWarpCreating(PlayerRef owner, PlayerWarpName name, Position location)
        implements PlayerWarpProposal {

    public PlayerWarpCreating {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
    }
}
