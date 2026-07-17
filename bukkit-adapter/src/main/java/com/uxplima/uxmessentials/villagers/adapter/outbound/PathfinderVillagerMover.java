package com.uxplima.uxmessentials.villagers.adapter.outbound;

import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.entity.Villager;

import com.destroystokyo.paper.entity.Pathfinder;
import org.jspecify.annotations.NullMarked;

/**
 * The production {@link VillagerMover}: it drives Paper's per-mob {@link Pathfinder} ({@code Villager#getPathfinder})
 * to walk the villager after its owner or halt it. This is the one leaf that touches the live pathfinder API, kept
 * behind the {@link VillagerMover} seam so the follow service stays testable.
 *
 * <p>Both calls run on the villager's own region thread, so touching the live entity here is region-safe.
 */
@NullMarked
public final class PathfinderVillagerMover implements VillagerMover {

    @Override
    public void moveTo(Villager villager, Location target, double speed) {
        Objects.requireNonNull(villager, "villager");
        Objects.requireNonNull(target, "target");
        villager.getPathfinder().moveTo(target, speed);
    }

    @Override
    public void stop(Villager villager) {
        Objects.requireNonNull(villager, "villager");
        villager.getPathfinder().stopPathfinding();
    }
}
