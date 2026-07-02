/**
 * The poses context's Bukkit listeners.
 * {@link com.uxplima.uxmessentials.poses.adapter.inbound.listener.SeatInteractListener} seats a player who
 * right-clicks a sittable block; {@link com.uxplima.uxmessentials.poses.adapter.inbound.listener.PoseCancelListener}
 * ends a pose on quit, teleport, damage, dismount, or sneak; and
 * {@link com.uxplima.uxmessentials.poses.adapter.inbound.listener.PoseCleanupListener} reaps tagged seats when a
 * chunk or world unloads, so a pose never leaves a ghost entity.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.poses.adapter.inbound.listener;
