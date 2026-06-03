/**
 * The player-warps context's domain events: the sealed {@code PlayerWarpEvent} family and its record
 * implementations. Each event records something that already happened ({@code PlayerWarpCreated},
 * {@code PlayerWarpDeleted}); the adapter bridges them to Bukkit events so other plugins observe player-warp
 * changes without importing this package.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.domain.event;
