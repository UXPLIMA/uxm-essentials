/**
 * The persistent player-data substrate for the menu engine: a {@code PlayerDataStore} that fronts the database-
 * backed {@code PlayerDataRepository} with an in-memory cache, so a menu's reads are entity-thread-safe cache hits
 * and its writes never block the tick thread on the database.
 *
 * <p>{@link com.uxplima.uxmessentials.shared.adapter.outbound.playerdata.CachingPlayerDataStore} warms a player's
 * rows on join and drops them on quit (driven by the lifecycle listener in the inbound package), mutates the cache
 * synchronously on a write, and persists that change asynchronously through the {@code Scheduler} port. The store
 * is durable, server-authoritative data; it is deliberately separate from the transient per-holder PDC the
 * {@code PlayerMeta} accessor manages.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.playerdata;
