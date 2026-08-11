/**
 * The presence context's outbound Bukkit adapters: {@code InMemoryPresenceStore} (the transient
 * {@code ConcurrentHashMap<UUID, PlayerPresence>} mutated through {@code compute}), {@code PdcNickStore} (the
 * per-player nickname), {@code BukkitPresenceAudience} (the online players an AFK broadcast reaches), and
 * {@code AfkSweep} (the self-rescheduling async idle scan that flips idle players to AFK on their
 * own region thread). Each Bukkit mutation hops to the owning region/entity thread through the kernel
 * {@code Scheduler} port.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.presence.adapter.outbound;
