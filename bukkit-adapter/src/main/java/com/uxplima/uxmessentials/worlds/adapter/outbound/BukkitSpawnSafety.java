package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.adapter.outbound.BukkitWarpSafetyChecker;
import com.uxplima.uxmessentials.worlds.application.port.SpawnSafety;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link SpawnSafety} a landing from another server asks: the same block check a warp landing passes, so a world
 * spawn and a warp are judged by one rule. Called on the region thread that owns the spawn.
 */
@NullMarked
public final class BukkitSpawnSafety implements SpawnSafety {

    private final BukkitWarpSafetyChecker blocks = new BukkitWarpSafetyChecker();

    @Override
    public boolean isSafe(Position position) {
        Objects.requireNonNull(position, "position");
        return blocks.isSafe(position);
    }
}
