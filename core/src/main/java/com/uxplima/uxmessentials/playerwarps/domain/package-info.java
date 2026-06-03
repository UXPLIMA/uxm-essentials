/**
 * Pure domain of the player-warps bounded context: the {@code PlayerWarp} value object (per-owner name,
 * position, public/private visibility, creation time), the {@code PlayerWarpName} per-owner identity, the
 * {@code PlayerWarpLimit} resolved quota, the {@code PlayerWarpError} failure enum, and the sealed
 * {@code PlayerWarpEvent} family. Player-warps mirror the server warps shape but are owned per player and
 * keyed by {@code (owner, name)} like homes, with a numbered-node count limit like homes. Access is by
 * ownership and the public flag — there is no per-warp permission node. No Bukkit, Paper, Kyori, or logging
 * type appears here; the model is built from value objects and the kernel primitives ({@code PlayerRef},
 * {@code WorldRef}, {@code Position}).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.domain;
