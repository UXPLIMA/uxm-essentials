/**
 * The inbound lifecycle wiring for the persistent player-data store: a Bukkit listener that warms a joining
 * player's rows into the {@code CachingPlayerDataStore} (off the tick thread) and drops them on quit, so the menu
 * engine's later reads are entity-thread-safe cache hits rather than cold database reads.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.playerdata;
