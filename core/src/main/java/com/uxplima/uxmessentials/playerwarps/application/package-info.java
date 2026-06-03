/**
 * The player-warps context's use cases and outbound ports. The use cases orchestrate the per-owner
 * {@code PlayerWarp} aggregate through the {@code PlayerWarpRepository} port, gate {@code /pwarp} by ownership
 * and the warp's public flag, resolve the per-owner count limit through {@code PlayerWarpQuota}, render
 * feedback through the {@code Messages}/{@code MessageSink} pair, and delegate the actual teleport to the
 * teleport context through the {@code PlayerWarpTeleporter} port — player-warps never re-implements movement.
 * The {@code PlayerwarpsModule} declares the context's commands and enable gate. No Bukkit, Paper, Kyori, or
 * logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.application;
