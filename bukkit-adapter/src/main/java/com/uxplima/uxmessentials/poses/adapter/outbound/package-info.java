/**
 * The poses context's outbound adapters. {@link com.uxplima.uxmessentials.poses.adapter.outbound.BukkitSeatPort}
 * spawns the invisible, non-persistent, PDC-tagged seat entities a sitting pose rides on, mounts the rider through
 * the Folia scheduler, and sweeps any tagged seat left behind by a crash so a pose never leaves a ghost;
 * {@link com.uxplima.uxmessentials.poses.adapter.outbound.BukkitPoseReturn} performs the region-aware
 * return-to-start teleport off the use case.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.poses.adapter.outbound;
