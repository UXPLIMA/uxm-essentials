package com.uxplima.uxmessentials.worlds.application.port;

import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * Resolves the destinations a void-rescue chain can name but the worlds context does not own: the spawn the
 * teleport context computes for a world, and a server warp held by the warps context. The worlds context owns
 * the policy and the coordinates; anything belonging to another context arrives through this port, the same
 * fence {@link WorldTeleporter} draws for the hop itself.
 */
public interface RescueTargets {

    /** The spawn a player falling out of {@code world} should land on, or empty when none resolves. */
    Optional<Position> spawn(WorldRef world);

    /** The named server warp's position, or empty when warps is disabled or the name is unknown. */
    Optional<Position> warp(String name);
}
