package com.uxplima.uxmessentials.worlds.application.port;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Judges whether a world's spawn is safe to land a player on: solid ground underfoot, room to stand, no lava or fire.
 * A platform boundary: the blocks are read through the server, on the region thread that owns {@code position}.
 */
@FunctionalInterface
public interface SpawnSafety {

    boolean isSafe(Position position);
}
