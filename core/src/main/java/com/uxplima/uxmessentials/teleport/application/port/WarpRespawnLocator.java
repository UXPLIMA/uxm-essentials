package com.uxplima.uxmessentials.teleport.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;

/** Non-blocking named-warp lookup used by the death respawn chain. */
@FunctionalInterface
public interface WarpRespawnLocator {

    /** Resolve {@code name} from an already-warmed snapshot, or empty when warps is disabled/missing. */
    Optional<Position> respawnWarp(String name);
}
