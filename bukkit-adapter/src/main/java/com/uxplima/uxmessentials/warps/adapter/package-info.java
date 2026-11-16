/**
 * The warps context's Bukkit adapters: the Brigadier command handlers (inbound), the teleport-delegating
 * {@code TeleportWarpAdapter} (outbound, driving the teleport context's gated engine), and the wiring that
 * constructs the use cases over the kernel ports and the jOOQ repository. The {@code Plugin} handle stays
 * in bootstrap; these adapters take only the injected ports. The per-warp cost soft-couples to the economy
 * context: with economy disabled the wiring injects an empty {@code WarpEconomy}, so a recorded cost is ignored
 * at use time rather than hard-failing.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.warps.adapter;
