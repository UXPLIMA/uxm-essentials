package com.uxplima.uxmessentials.playerwarps.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player warp is about to be deleted for good.
 *
 * @param owner whose warp it is
 * @param name which warp
 */
public record PlayerWarpDeleting(PlayerRef owner, PlayerWarpName name) implements PlayerWarpProposal {

    public PlayerWarpDeleting {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
    }
}
