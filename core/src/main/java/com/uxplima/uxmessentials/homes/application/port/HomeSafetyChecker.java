package com.uxplima.uxmessentials.homes.application.port;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Whether a home's spot is dangerous to stand in (inside blocks, over lava, in mid-air). A platform boundary: the
 * answer reads the world's blocks, so the adapter behind it is Bukkit-side and must be called on the region thread
 * that owns {@code at}. A cross-server landing asks it there before it moves the player, since only the target backend
 * holds the home's world.
 */
public interface HomeSafetyChecker {

    /** True when {@code at} is demonstrably unsafe; a spot that cannot be judged here reads as safe. */
    boolean isUnsafe(Position at);
}
