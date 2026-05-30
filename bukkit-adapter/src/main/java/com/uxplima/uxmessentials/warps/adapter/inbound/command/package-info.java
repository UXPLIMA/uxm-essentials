/**
 * The warps context's inbound Brigadier command handlers — {@code /warp}, {@code /setwarp},
 * {@code /delwarp}, {@code /warps}, {@code /warpinfo}, {@code /movewarp}. Each maps a command source and its
 * arguments onto exactly one warps use-case call; all player-facing feedback flows through the use cases'
 * {@code MessageSink}, and only the players-only rejection a console may see is rendered here, still through
 * the catalog. The base command permission gates the command itself; the per-warp
 * {@code uxmessentials.warp.use.<warp>} gate and the optional cost are checked inside the {@code UseWarp}
 * use case per destination.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.warps.adapter.inbound.command;
