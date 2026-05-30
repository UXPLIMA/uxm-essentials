/**
 * The warps context's outbound Bukkit adapter: {@code TeleportWarpAdapter}, the {@code WarpTeleporter}
 * implementation that delegates a warp teleport to the teleport context's gated engine, so the warps
 * context never re-implements movement. The optional economy bridge ({@code WarpEconomy}) is supplied by
 * the economy context when it lands (P3); until then the warps wiring injects an empty provider and a
 * warp's cost is ignored at use time.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.warps.adapter.outbound;
