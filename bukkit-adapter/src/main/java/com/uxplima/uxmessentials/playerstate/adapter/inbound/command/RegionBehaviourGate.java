package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

/**
 * Whether a region where a player stands forbids a playerstate toggle, read from the guard module's region
 * behaviours ({@code fly = deny}, {@code god = deny}). {@link #NONE} when the guard module is off.
 */
@NullMarked
@FunctionalInterface
public interface RegionBehaviourGate {

    /** Nothing forbids anything: the guard module is off. */
    RegionBehaviourGate NONE = (player, behaviour) -> false;

    /** True when a region at {@code player}'s position sets {@code behaviour} to deny. */
    boolean denies(Player player, String behaviour);
}
