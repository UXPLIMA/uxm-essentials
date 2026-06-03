/**
 * The player-warps context's outbound bukkit adapter: {@code TeleportPlayerWarpAdapter}, which implements the
 * {@code PlayerWarpTeleporter} port by delegating to the teleport context's {@code TeleportEngine}. The
 * player-warps context never re-implements movement; this is the one seam that hands a resolved warp's
 * position to the engine's gated launch.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.adapter.outbound;
