/**
 * The homes context's outbound Bukkit adapters: {@code TeleportHomeAdapter}, the {@code HomeTeleporter}
 * implementation that delegates a home teleport to the teleport context's gated engine; and
 * {@code SafeLocationGuard}, the {@link com.uxplima.uxmessentials.homes.application.port.SethomeGuard}
 * that rejects placements in dangerous spots (solid blocks at feet/head, lava below, or no ground
 * within the configured mid-air depth).
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.homes.adapter.outbound;
