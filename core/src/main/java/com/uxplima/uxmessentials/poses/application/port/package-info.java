/**
 * The poses context's outbound ports. {@link com.uxplima.uxmessentials.poses.application.port.SeatPort} spawns,
 * mounts, removes, and sweeps the invisible seat entities a pose rides on (the adapter tags each with a PDC key
 * and reaps orphans so a pose never leaves a ghost); {@link com.uxplima.uxmessentials.poses.application.port.PoseReturn}
 * puts a player back where a pose began when {@code return-to-start} is on;
 * {@link com.uxplima.uxmessentials.poses.application.port.PoseRegionGate} decides whether a player may pose at a
 * position, so a claim or WorldGuard veto (Phase 5) plugs in without the use cases changing; and
 * {@link com.uxplima.uxmessentials.poses.application.port.CrawlView} shows and restores the client-only fake block
 * behind {@code /crawl} (the adapter sends it to a single player, never placing a real block, and always restores
 * it so no phantom is left on screen). The adapters live in the bukkit module. Pure Java: no Bukkit, Paper, Kyori,
 * or SLF4J.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.poses.application.port;
