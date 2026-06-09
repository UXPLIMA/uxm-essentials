package com.uxplima.uxmessentials.warps.application.port;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Gated verification checking if a warp destination is physically safe for a player to be teleported to.
 */
public interface WarpSafetyChecker {

    /**
     * Checks if {@code position} is safe (e.g. solid ground underfoot, no suffocation hazards or lava).
     */
    boolean isSafe(Position position);
}
