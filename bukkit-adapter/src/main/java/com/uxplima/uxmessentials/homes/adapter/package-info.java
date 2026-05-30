/**
 * The homes context's Bukkit adapters: the Brigadier command handlers (inbound), the teleport-delegating
 * {@code TeleportHomeAdapter} (outbound, driving the teleport context's gated engine), and the wiring that
 * constructs the use cases over the kernel ports and the jOOQ repository. The {@code Plugin} handle stays
 * in bootstrap; these adapters take only the {@code Plugin} interface and the injected ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.homes.adapter;
