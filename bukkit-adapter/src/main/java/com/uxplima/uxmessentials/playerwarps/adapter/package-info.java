/**
 * The player-warps context's bukkit adapter: {@code PlayerwarpsWiring} constructs the use cases over the
 * kernel ports, the cached jOOQ repository, and the teleport-delegating teleporter; {@code PlayerWarpServices}
 * holds them for the Brigadier commands under {@code adapter.inbound.command}; the teleport delegation lives
 * under {@code adapter.outbound}. This is the only place the context's classes are wired.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerwarps.adapter;
