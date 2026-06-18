/**
 * The worlds context's outbound Bukkit adapter: {@code BukkitWorldEngine}, the anti-corruption layer
 * that drives Bukkit's {@code WorldCreator}/{@code Server}/{@code World} APIs and the world folder on
 * disk on behalf of the worlds use cases, and {@code InMemoryPendingDeletionRegistry}, the short-lived
 * delete-confirm staging keyed by requester. This is the only worlds package that imports
 * {@code org.bukkit}; the domain and application stay free of it.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.adapter.outbound;
