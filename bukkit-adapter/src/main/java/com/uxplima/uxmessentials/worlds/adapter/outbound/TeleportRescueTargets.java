package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.worlds.application.port.RescueTargets;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves the two rescue destinations the worlds context does not own, by delegating to the seams bootstrap
 * already holds: the teleport context's spawn resolution and the warps module's warmed lookup. Both arrive as
 * plain functions so the worlds adapter keeps its single documented dependency on {@code teleport.*} inside
 * {@link BukkitWorldTeleporter}, and so a disabled warps module degrades to "no warp step resolves" rather
 * than to a wiring failure.
 */
@NullMarked
public final class TeleportRescueTargets implements RescueTargets {

    private final Function<WorldRef, Optional<Position>> spawns;
    private final Function<String, Optional<Position>> warps;

    public TeleportRescueTargets(
            Function<WorldRef, Optional<Position>> spawns, Function<String, Optional<Position>> warps) {
        this.spawns = Objects.requireNonNull(spawns, "spawns");
        this.warps = Objects.requireNonNull(warps, "warps");
    }

    @Override
    public Optional<Position> spawn(WorldRef world) {
        Objects.requireNonNull(world, "world");
        return spawns.apply(world);
    }

    @Override
    public Optional<Position> warp(String name) {
        Objects.requireNonNull(name, "name");
        return warps.apply(name);
    }
}
