/**
 * The playerstate context's outbound Bukkit adapters: {@code InMemoryPlayerStateStore} (the transient
 * {@code ConcurrentHashMap<UUID, PlayerStateSnapshot>} mutated via {@code compute}), {@code BukkitStateReconciler}
 * (push a snapshot to the live player on its owning region thread via the {@code Scheduler} port),
 * {@code BukkitPlayerEffects} (heal/feed/extinguish/suicide/night-vision/ptime/pweather, each hopped to the
 * entity thread), and {@code BukkitNearbyPlayers} (the {@code /near} scan). These are the only classes in the
 * context that touch the Bukkit API for state mutation; the use cases stay pure.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.adapter.outbound;
